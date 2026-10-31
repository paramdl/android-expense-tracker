package com.expensetracker.data.imports

import com.expensetracker.data.Account
import com.expensetracker.data.Category
import com.expensetracker.data.PayKind
import com.expensetracker.data.Repo
import com.expensetracker.data.Transaction
import com.expensetracker.data.TxSource
import com.expensetracker.data.TxType
import java.util.Calendar

/**
 * Maps a spreadsheet of transactions onto the ledger.
 *
 * Column names vary between the apps people export from, so headers are matched
 * by a set of aliases rather than a fixed position. Categories, accounts and
 * payment methods that do not exist yet are created as the rows are read.
 */
object LedgerImport {

    data class Mapping(
        val date: Int = -1,
        val amount: Int = -1,
        val type: Int = -1,
        val category: Int = -1,
        val subcategory: Int = -1,
        val note: Int = -1,
        val description: Int = -1,
        val account: Int = -1,
        val method: Int = -1
    ) {
        val usable: Boolean get() = date >= 0 && amount >= 0
    }

    data class Preview(
        val mapping: Mapping,
        val header: List<String>,
        val totalRows: Int,
        val readable: Int,
        val samples: List<String>,
        val problem: String? = null
    )

    data class Result(
        val imported: Int,
        val skipped: Int,
        val duplicates: Int,
        val error: String? = null
    )

    // Header aliases, lower-cased. First match wins.
    private val DATE = listOf("date", "transaction date", "txn date", "period", "time")
    private val AMOUNT = listOf("inr", "amount", "value", "debit/credit", "money")
    private val TYPE = listOf("income/expense", "type", "transaction type", "dr/cr")
    private val CATEGORY = listOf("category", "main category")
    private val SUBCATEGORY = listOf("subcategory", "sub category", "sub-category")
    private val NOTE = listOf("note", "payee", "merchant", "title", "name")
    private val DESCRIPTION = listOf("description", "remark", "remarks", "comment", "memo")
    private val ACCOUNT = listOf("account", "wallet", "source")
    private val METHOD = listOf("payment method", "method", "mode", "payment mode")

    fun detect(sheet: Sheet): Preview {
        val h = sheet.header.map { it.trim().lowercase() }
        fun find(aliases: List<String>, skip: Int = 0): Int {
            var seen = 0
            aliases.forEach { a ->
                h.forEachIndexed { i, name ->
                    if (name == a) {
                        if (seen == skip) return i
                        seen++
                    }
                }
            }
            return -1
        }

        val accountIdx = find(ACCOUNT)
        val mapping = Mapping(
            date = find(DATE),
            amount = find(AMOUNT),
            type = find(TYPE),
            category = find(CATEGORY),
            subcategory = find(SUBCATEGORY),
            note = find(NOTE),
            description = find(DESCRIPTION),
            account = accountIdx,
            // Money Manager exports put the instrument ("UPI Transfer", "Credit
            // Card", "Cash") in the Account column, so it doubles as the method.
            method = if (find(METHOD) >= 0) find(METHOD) else accountIdx
        )

        if (sheet.header.isEmpty()) {
            return Preview(mapping, sheet.header, 0, 0, emptyList(), "That file has no header row.")
        }
        if (!mapping.usable) {
            val missing = buildList {
                if (mapping.date < 0) add("a date column")
                if (mapping.amount < 0) add("an amount column")
            }.joinToString(" and ")
            return Preview(
                mapping, sheet.header, sheet.rows.size, 0, emptyList(),
                "Could not find " + missing + ". Columns seen: " + sheet.header.joinToString(", ")
            )
        }

        var readable = 0
        val samples = ArrayList<String>()
        sheet.rows.forEach { row ->
            val parsed = readRow(row, mapping)
            if (parsed != null) {
                readable++
                if (samples.size < 3) {
                    samples.add(
                        (if (parsed.type == TxType.INCOME) "+" else "-") +
                            String.format("%,.2f", parsed.amount) +
                            "  " + parsed.merchant.ifBlank { parsed.categoryName.ifBlank { "—" } }
                    )
                }
            }
        }
        return Preview(mapping, sheet.header, sheet.rows.size, readable, samples)
    }

    private data class Row(
        val date: Long,
        val amount: Double,
        val type: TxType,
        val categoryName: String,
        val merchant: String,
        val note: String,
        val accountName: String,
        val methodName: String
    )

    private fun cell(row: List<String>, index: Int): String =
        if (index in row.indices) row[index].trim() else ""

    private fun readRow(row: List<String>, m: Mapping): Row? {
        val rawDate = cell(row, m.date)
        val rawAmount = cell(row, m.amount)
        if (rawDate.isEmpty() || rawAmount.isEmpty()) return null

        val date = parseDate(rawDate) ?: return null
        val amount = parseAmount(rawAmount) ?: return null
        if (amount <= 0.0) return null

        val typeText = cell(row, m.type).lowercase()
        val type = when {
            typeText.startsWith("income") || typeText == "credit" || typeText == "cr" -> TxType.INCOME
            typeText.startsWith("transfer") -> TxType.TRANSFER
            else -> TxType.EXPENSE
        }

        val category = listOf(cell(row, m.category), cell(row, m.subcategory))
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return Row(
            date = date,
            amount = amount,
            type = type,
            categoryName = category,
            merchant = cell(row, m.note),
            note = cell(row, m.description),
            accountName = cell(row, m.account),
            methodName = cell(row, m.method)
        )
    }

    /** Accepts Excel serial numbers and the common written formats. */
    fun parseDate(raw: String): Long? {
        val serial = raw.toDoubleOrNull()
        if (serial != null) {
            // Excel serials for any plausible transaction sit well above 1000
            // (1902) and below 80000 (2119); anything else is not a date.
            if (serial < 1000 || serial > 80000) return null
            return Tabular.excelSerialToMillis(serial)
        }

        val digits = Regex("""(\d{1,4})[-/.](\d{1,2})[-/.](\d{2,4})""").find(raw) ?: return null
        val (a, b, c) = digits.destructured
        val first = a.toInt(); val second = b.toInt(); val third = c.toInt()

        // Either yyyy-mm-dd or dd-mm-yyyy; a 4-digit leading group means the former.
        val (year, month, day) = if (a.length == 4) {
            Triple(first, second, third)
        } else {
            Triple(if (third < 100) 2000 + third else third, second, first)
        }
        if (month !in 1..12 || day !in 1..31) return null

        val time = Regex("""(\d{1,2}):(\d{2})""").find(raw)
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day,
                time?.groupValues?.get(1)?.toIntOrNull() ?: 12,
                time?.groupValues?.get(2)?.toIntOrNull() ?: 0, 0)
        }.timeInMillis
    }

    private val NUMBER = Regex("""-?\d[\d,]*(?:\.\d+)?""")

    /**
     * Strips currency symbols and thousands separators; keeps the magnitude.
     *
     * Extracts the numeric token rather than deleting unwanted characters:
     * blanket deletion turned "Rs. 2,000" into ".2000", i.e. twenty paise.
     */
    fun parseAmount(raw: String): Double? {
        val token = NUMBER.find(raw.replace(" ", "")) ?: return null
        val v = token.value.replace(",", "").toDoubleOrNull() ?: return null
        return kotlin.math.abs(v)
    }

    /**
     * Writes the sheet into the ledger. Rows already present — same date, amount
     * and merchant — are counted as duplicates and skipped, so re-importing the
     * same export does not double the books.
     */
    fun run(repo: Repo, sheet: Sheet, mapping: Mapping): Result {
        if (!mapping.usable) return Result(0, 0, 0, "The date and amount columns are needed.")

        var imported = 0
        var skipped = 0
        var duplicates = 0

        try {
            repo.batch {
                val categories = HashMap<String, Long>()
                repo.categories(true).forEach { categories[key(it.name, it.type)] = it.id }
                val accounts = HashMap<String, Long>()
                repo.accounts(true).forEach { accounts[it.name.lowercase()] = it.id }
                val methods = HashMap<String, Long>()
                repo.payTags(PayKind.METHOD).forEach { methods[it.name.lowercase()] = it.id }

                val existing = HashSet<String>()
                repo.transactions().forEach { existing.add(fingerprint(it.tx.date, it.tx.amount, it.tx.merchant)) }

                sheet.rows.forEach { raw ->
                    val row = readRow(raw, mapping)
                    if (row == null) { skipped++; return@forEach }

                    val print = fingerprint(row.date, row.amount, row.merchant)
                    if (!existing.add(print)) { duplicates++; return@forEach }

                    val categoryId = if (row.categoryName.isBlank() || row.type == TxType.TRANSFER) null
                    else {
                        val (emoji, plainName) = splitEmoji(row.categoryName)
                        val name = plainName.ifBlank { row.categoryName }
                        categories.getOrPut(key(name, row.type)) {
                            repo.upsertCategory(
                                Category(
                                    name = name,
                                    type = if (row.type == TxType.INCOME) TxType.INCOME else TxType.EXPENSE,
                                    emoji = emoji ?: "📁",
                                    colorArgb = colorFor(name)
                                )
                            )
                        }
                    }

                    val accountId = row.accountName.takeIf { it.isNotBlank() }?.let { name ->
                        accounts.getOrPut(name.lowercase()) {
                            repo.upsertAccount(Account(name = name, emoji = "🏦"))
                        }
                    }

                    val methodId = row.methodName.takeIf { it.isNotBlank() }?.let { name ->
                        methods.getOrPut(name.lowercase()) {
                            repo.payTagByNameOrCreate(PayKind.METHOD, name)
                        }
                    }

                    repo.upsertTx(
                        Transaction(
                            type = row.type,
                            amount = row.amount,
                            date = row.date,
                            categoryId = categoryId,
                            personId = null,
                            accountId = accountId,
                            merchant = row.merchant,
                            note = row.note,
                            source = TxSource.IMPORT,
                            methodId = methodId
                        )
                    )
                    imported++
                }
            }
        } catch (e: Exception) {
            return Result(imported, skipped, duplicates, e.message ?: "Import failed part-way.")
        }
        return Result(imported, skipped, duplicates)
    }

    private fun key(name: String, type: TxType) =
        name.lowercase() + "|" + (if (type == TxType.INCOME) "I" else "E")

    /** Same day, same amount, same payee counts as the same transaction. */
    private fun fingerprint(date: Long, amount: Double, merchant: String): String =
        (date / 60_000L).toString() + "|" + Math.round(amount * 100) + "|" + merchant.trim().lowercase()

    /**
     * Money Manager decorates category names with an emoji, at either end
     * ("🥞 Food", "Shopping For Home 🏡"). Lift it out as the icon and return the
     * bare name, otherwise the chip shows the emoji twice.
     */
    fun splitEmoji(name: String): Pair<String?, String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null to trimmed

        val firstCp = trimmed.codePointAt(0)
        if (isPictographic(firstCp)) {
            val emoji = String(Character.toChars(firstCp))
            return emoji to trimmed.substring(Character.charCount(firstCp)).trim()
        }

        val lastCp = trimmed.codePointBefore(trimmed.length)
        if (isPictographic(lastCp)) {
            val emoji = String(Character.toChars(lastCp))
            return emoji to trimmed.dropLast(Character.charCount(lastCp)).trim()
        }
        return null to trimmed
    }

    /** Emoji and pictograph blocks, excluding ordinary punctuation and letters. */
    private fun isPictographic(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2190..0x21FF ||
            cp in 0xFE00..0xFE0F || cp in 0x1F000..0x1F2FF

    private val PALETTE = listOf(
        0xFFE06C9F, 0xFF7C6BD6, 0xFF3FA372, 0xFFE0783C, 0xFF3D8FD1, 0xFFD1A73D,
        0xFF8C6E4F, 0xFFD64550, 0xFF4C6EDB, 0xFFB05CD6, 0xFF2FA8A8, 0xFFD6707C
    )

    private fun colorFor(name: String): Int =
        PALETTE[Math.floorMod(name.lowercase().hashCode(), PALETTE.size)].toInt()
}
