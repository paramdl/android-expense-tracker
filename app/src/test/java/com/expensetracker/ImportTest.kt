package com.expensetracker

import com.expensetracker.data.TxType
import com.expensetracker.data.imports.LedgerImport
import com.expensetracker.data.imports.Sheet
import com.expensetracker.data.imports.SheetFormat
import com.expensetracker.data.imports.Tabular
import com.expensetracker.data.imports.UnreadableSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

/**
 * Import feeds straight into the ledger, so a misread date or amount is a
 * silent corruption. These pin down the formats the real export actually uses.
 */
class ImportTest {

    private fun ymd(t: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // ------------------------------------------------------------ excel dates

    @Test
    fun `excel serial converts to the same calendar day`() {
        // 46266.69 is 2026-09-01 in the sample export.
        assertEquals(Triple(2026, 9, 1), ymd(Tabular.excelSerialToMillis(46266.69043177083)))
        // 25569 is the Excel serial for 1970-01-01.
        assertEquals(Triple(1970, 1, 1), ymd(Tabular.excelSerialToMillis(25569.0)))
        assertEquals(Triple(2025, 8, 19), ymd(Tabular.excelSerialToMillis(45888.5)))
    }

    @Test
    fun `a serial just after midnight does not slip to the previous day`() {
        // 46266.0 is midnight; a naive UTC conversion lands on the day before
        // for anyone east of Greenwich.
        assertEquals(Triple(2026, 9, 1), ymd(Tabular.excelSerialToMillis(46266.0)))
    }

    @Test
    fun `written date formats are understood`() {
        assertEquals(Triple(2026, 8, 25), ymd(LedgerImport.parseDate("2026-08-25")!!))
        assertEquals(Triple(2026, 8, 25), ymd(LedgerImport.parseDate("25/08/2026")!!))
        assertEquals(Triple(2026, 8, 25), ymd(LedgerImport.parseDate("25-08-26 14:30")!!))
        assertNull(LedgerImport.parseDate("not a date"))
        assertNull(LedgerImport.parseDate(""))
    }

    // ---------------------------------------------------------------- amounts

    @Test
    fun `amounts survive currency symbols and separators`() {
        assertEquals(5482.0, LedgerImport.parseAmount("5482.0")!!, 0.001)
        assertEquals(1299.50, LedgerImport.parseAmount("₹1,299.50")!!, 0.001)
        assertEquals(2000.0, LedgerImport.parseAmount("Rs. 2,000")!!, 0.001)
        // A negative in the sheet still means "this much money moved".
        assertEquals(450.0, LedgerImport.parseAmount("-450")!!, 0.001)
        assertNull(LedgerImport.parseAmount(""))
        assertNull(LedgerImport.parseAmount("abc"))
    }

    // ----------------------------------------------------------- entity decode

    @Test
    fun `category emoji written as entities are decoded`() {
        assertEquals("🥨 Snacks", Tabular.decode("&#x1f968; Snacks"))
        assertEquals("Shopping For Home 🏡", Tabular.decode("Shopping For Home &#x1f3e1;"))
        assertEquals("Tea & Coffee", Tabular.decode("Tea &amp; Coffee"))
        assertEquals("plain", Tabular.decode("plain"))
    }

    // -------------------------------------------------------------------- CSV

    @Test
    fun `csv with quotes commas and embedded newlines`() {
        val sheet = Tabular.parseCsv(
            "Date,Amount,Note\n" +
                "2026-08-25,\"1,299.50\",\"Bought \"\"stuff\"\", twice\"\n" +
                "2026-08-26,450,Simple\n"
        )
        assertEquals(listOf("Date", "Amount", "Note"), sheet.header)
        assertEquals(2, sheet.rows.size)
        assertEquals("1,299.50", sheet.rows[0][1])
        assertEquals("Bought \"stuff\", twice", sheet.rows[0][2])
    }

    // ---------------------------------------------------- column auto-detection

    @Test
    fun `money manager headers are detected`() {
        val sheet = Sheet(
            header = listOf(
                "Date", "Account", "Category", "Subcategory", "Note", "INR",
                "Income/Expense", "Description", "Amount", "Currency", "Account"
            ),
            rows = listOf(
                listOf("46266.69", "UPI Transfer", "EMI Repay", "", "Kreditbee", "5482.0",
                    "Expense", "", "5482.0", "INR", "5482.0")
            )
        )
        val p = LedgerImport.detect(sheet)
        assertTrue(p.problem, p.mapping.usable)
        assertEquals(0, p.mapping.date)
        assertEquals(5, p.mapping.amount)          // the INR column, not "Amount"
        assertEquals(6, p.mapping.type)
        assertEquals(2, p.mapping.category)
        assertEquals(4, p.mapping.note)
        assertEquals(1, p.mapping.account)
        assertEquals(1, p.rows())
    }

    private fun LedgerImport.Preview.rows() = readable

    @Test
    fun `transfer-out and income rows are typed correctly`() {
        val header = listOf("Date", "INR", "Income/Expense", "Note")
        fun typeOf(kind: String): TxType {
            val sheet = Sheet(header, listOf(listOf("46266.5", "100", kind, "x")))
            val p = LedgerImport.detect(sheet)
            assertTrue(p.mapping.usable)
            return p.samples.firstOrNull()?.let {
                if (it.startsWith("+")) TxType.INCOME else TxType.EXPENSE
            } ?: TxType.EXPENSE
        }
        assertEquals(TxType.INCOME, typeOf("Income"))
        assertEquals(TxType.EXPENSE, typeOf("Expense"))
    }

    @Test
    fun `a sheet without date or amount is reported not silently accepted`() {
        val p = LedgerImport.detect(Sheet(listOf("Foo", "Bar"), listOf(listOf("1", "2"))))
        assertTrue(!p.mapping.usable)
        assertNotNull(p.problem)
    }

    @Test
    fun `category emoji is lifted out of the name, not duplicated`() {
        // Leading emoji, as Money Manager writes most categories.
        assertEquals("🥞" to "Food", LedgerImport.splitEmoji("🥞 Food"))
        // Trailing emoji.
        assertEquals("🏡" to "Shopping For Home", LedgerImport.splitEmoji("Shopping For Home 🏡"))
        // No emoji: the name is returned untouched.
        assertEquals(null to "EMI Repay", LedgerImport.splitEmoji("EMI Repay"))
        // A plain name ending in a letter must not lose its last character.
        assertEquals(null to "Yasho Amma", LedgerImport.splitEmoji("Yasho Amma"))
    }

    // ------------------------------------------------- format detection

    private fun tmp(name: String, bytes: ByteArray): File {
        val f = File.createTempFile(name, null)
        f.deleteOnExit()
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun `format is judged by content, not by the file name`() {
        val csv = "Date,Amount\n2026-09-01,100\n".toByteArray()
        assertEquals(SheetFormat.TEXT, Tabular.sniff(csv))
        // A ZIP, which is what an .xlsx really is.
        assertEquals(
            SheetFormat.XLSX,
            Tabular.sniff(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        )
        // The pre-2007 binary .xls: an OLE2 compound file.
        assertEquals(
            SheetFormat.LEGACY_XLS,
            Tabular.sniff(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0))
        )
        assertEquals(SheetFormat.EMPTY, Tabular.sniff(ByteArray(0)))
    }

    @Test
    fun `a CSV saved with an xls extension still imports`() {
        // The regression: this used to reach the ZIP reader and fail with
        // "zip END header not found" purely because of the file name.
        val f = tmp("export.xls", "Date,Amount,Note\n2026-09-01,100,Tea\n".toByteArray())
        val sheet = Tabular.parse(f)
        assertEquals(listOf("Date", "Amount", "Note"), sheet.header)
        assertEquals(1, sheet.rows.size)
    }

    @Test
    fun `a real legacy xls is refused with an instruction, not a stack trace`() {
        val ole2 = ByteArray(64)
        listOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
            .forEachIndexed { i, b -> ole2[i] = b.toByte() }
        val f = tmp("old.xls", ole2)
        try {
            Tabular.parse(f)
            throw AssertionError("expected UnreadableSheet")
        } catch (e: UnreadableSheet) {
            assertTrue(e.message!!, e.message!!.contains("save it as .xlsx or .csv"))
        }
    }

    @Test
    fun `an empty file is named as empty rather than reported as a broken zip`() {
        try {
            Tabular.parse(tmp("nothing.csv", ByteArray(0)))
            throw AssertionError("expected UnreadableSheet")
        } catch (e: UnreadableSheet) {
            assertTrue(e.message!!, e.message!!.contains("empty"))
        }
    }

    @Test
    fun `a UTF-16 CSV is decoded instead of read as rubbish`() {
        // Excel's "Unicode Text" export. Read as UTF-8 the header would come
        // back interleaved with nulls and no column would ever match.
        val text = "Date,Amount\n2026-09-01,250\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        val sheet = Tabular.parse(tmp("unicode.csv", bytes))
        assertEquals(listOf("Date", "Amount"), sheet.header)
        assertEquals(1, sheet.rows.size)
    }
}
