package com.expensetracker.data.imports

import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.ZipFile

/** A parsed sheet: a header row plus the rows beneath it. */
data class Sheet(val header: List<String>, val rows: List<List<String>>)

/**
 * A file this reader cannot open, carrying a message meant for the user
 * rather than a stack trace.
 */
class UnreadableSheet(message: String) : Exception(message)

/** What a file actually is, judged by its first bytes rather than its name. */
enum class SheetFormat { XLSX, TEXT, LEGACY_XLS, EMPTY }

/**
 * Reads CSV and XLSX into a plain table, with no third-party dependency.
 *
 * XLSX is a ZIP of XML, so the reader unzips it and pulls `sharedStrings.xml`
 * and the first worksheet directly. That keeps the APK small — a spreadsheet
 * library would add several megabytes for one screen.
 */
object Tabular {

    // -------------------------------------------------------------- detection

    /**
     * Identifies a file from its leading bytes.
     *
     * The name is not consulted at all. Exports arrive with the wrong
     * extension often enough — a CSV saved as .xls, a sheet downloaded with no
     * extension at all — that trusting it sends good files to the wrong parser.
     */
    fun sniff(head: ByteArray): SheetFormat = when {
        head.isEmpty() -> SheetFormat.EMPTY
        // Any ZIP local-file, empty-archive or spanned header: .xlsx is a ZIP.
        head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte()) ->
            SheetFormat.XLSX
        // OLE2 compound file: the pre-2007 binary .xls, a different format.
        head.size >= 4 && head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() &&
            head[2] == 0x11.toByte() && head[3] == 0xE0.toByte() ->
            SheetFormat.LEGACY_XLS
        else -> SheetFormat.TEXT
    }

    /**
     * Decodes text honouring a byte-order mark.
     *
     * Excel's "Unicode Text" export is UTF-16, which read as UTF-8 turns every
     * row into nulls and interleaved rubbish rather than failing outright.
     */
    fun decodeText(bytes: ByteArray): String {
        val utf16le = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        val utf16be = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        return when {
            utf16le -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            utf16be -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> bytes.decodeToString()
        }
    }

    /**
     * Reads a staged local file as a sheet, choosing the parser by content.
     *
     * Throws [UnreadableSheet] with a message fit to show the user for formats
     * that genuinely cannot be read.
     */
    fun parse(file: File): Sheet {
        val head = file.inputStream().use { input ->
            val buf = ByteArray(8)
            val n = input.read(buf)
            if (n <= 0) ByteArray(0) else buf.copyOf(n)
        }
        return when (sniff(head)) {
            SheetFormat.EMPTY -> throw UnreadableSheet("That file is empty.")
            SheetFormat.LEGACY_XLS -> throw UnreadableSheet(
                "That is an older .xls spreadsheet, which this reader cannot open. " +
                    "Open it in Excel or Google Sheets and save it as .xlsx or .csv, " +
                    "then try again."
            )
            SheetFormat.XLSX -> {
                val sheet = runCatching { parseXlsx(file) }.getOrElse {
                    throw UnreadableSheet(
                        "That looks like a zipped file rather than a spreadsheet. " +
                            "Export again as .xlsx or .csv and try that."
                    )
                }
                if (sheet.header.isEmpty()) throw UnreadableSheet(
                    "No worksheet was found inside that file. Export it again as " +
                        ".xlsx or .csv."
                )
                sheet
            }
            SheetFormat.TEXT -> {
                val sheet = parseCsv(decodeText(file.readBytes()))
                if (sheet.header.isEmpty()) throw UnreadableSheet(
                    "That file has no rows this reader could recognise. A CSV needs a " +
                        "header row with a date and an amount column."
                )
                sheet
            }
        }
    }

    // ------------------------------------------------------------------- CSV

    /** Splits CSV, honouring quoted fields, escaped quotes and embedded newlines. */
    fun parseCsv(text: String): Sheet {
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")   // strip a UTF-8 BOM

        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows.add(ArrayList(row))
            row.clear()
        }

        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && (c == ',' || c == ';' || c == '\t') -> endCell()
                !inQuotes && c == '\n' -> endRow()
                !inQuotes && c == '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        endRow()

        if (rows.isEmpty()) return Sheet(emptyList(), emptyList())
        return Sheet(rows.first().map { it.trim() }, rows.drop(1))
    }

    // ------------------------------------------------------------------ XLSX

    /**
     * Reads an .xlsx from a local file.
     *
     * Uses [ZipFile] rather than a streaming reader: exporters commonly write
     * entries with a trailing data descriptor and a zero size in the local
     * header, which makes ZipInputStream throw "invalid entry size". ZipFile
     * reads the central directory instead and handles those files.
     */
    fun parseXlsx(file: File): Sheet {
        var sharedXml: String? = null
        var sheetXml: String? = null
        var firstSheetName: String? = null

        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" ->
                        sharedXml = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
                    // Worksheets are not always named sheet1.xml, so take the first.
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        if (firstSheetName == null || name < firstSheetName!!) {
                            firstSheetName = name
                            sheetXml = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
                        }
                    }
                }
            }
        }

        val sheet = sheetXml ?: return Sheet(emptyList(), emptyList())
        val shared = sharedXml?.let { readSharedStrings(it) } ?: emptyList()
        return readSheet(sheet, shared)
    }

    private val SI = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val T_TAG = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
    private val ROW = Regex("""<row[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("""<c([^>]*)/>|<c([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL)
    private val V_TAG = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val REF = Regex("""r="([A-Z]+)\d+"""")
    private val TYPE = Regex("""t="([^"]+)"""")

    private fun readSharedStrings(xml: String): List<String> =
        SI.findAll(xml).map { si ->
            // A styled string is split across several <t> runs; join them.
            T_TAG.findAll(si.groupValues[1]).joinToString("") { decode(it.groupValues[1]) }
        }.toList()

    private fun readSheet(xml: String, shared: List<String>): Sheet {
        val out = ArrayList<List<String>>()
        var width = 0

        ROW.findAll(xml).forEach { rowMatch ->
            val cells = HashMap<Int, String>()
            CELL.findAll(rowMatch.groupValues[1]).forEach { m ->
                val attrs = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] else m.groupValues[2]
                val body = m.groupValues[3]
                // Missing cells are simply absent from the XML, so the column
                // index has to come from the cell reference or values shift left.
                val col = REF.find(attrs)?.groupValues?.get(1)?.let { columnIndex(it) } ?: cells.size
                val type = TYPE.find(attrs)?.groupValues?.get(1)
                val raw = V_TAG.find(body)?.groupValues?.get(1) ?: ""
                val value = when {
                    type == "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    type == "inlineStr" -> T_TAG.find(body)?.groupValues?.get(1)?.let { decode(it) } ?: ""
                    else -> decode(raw)
                }
                cells[col] = value
                if (col + 1 > width) width = col + 1
            }
            if (cells.isNotEmpty()) {
                out.add((0 until width).map { cells[it] ?: "" })
            }
        }

        if (out.isEmpty()) return Sheet(emptyList(), emptyList())
        val header = out.first().map { it.trim() }
        val body = out.drop(1).filter { r -> r.any { it.isNotBlank() } }
        // Pad short rows so column lookups by index are always safe.
        val padded = body.map { r -> if (r.size >= header.size) r else r + List(header.size - r.size) { "" } }
        return Sheet(header, padded)
    }

    private fun columnIndex(letters: String): Int {
        var n = 0
        letters.forEach { n = n * 26 + (it - 'A' + 1) }
        return n - 1
    }

    /**
     * Decodes XML escapes and numeric character references. Money Manager writes
     * category emoji as `&#x1f968;`, which would otherwise show as literal text.
     */
    fun decode(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { sb.append(c); i++; continue }
            val end = s.indexOf(';', i + 1)
            if (end == -1 || end - i > 12) { sb.append(c); i++; continue }
            when (val entity = s.substring(i + 1, end)) {
                "amp" -> sb.append('&')
                "lt" -> sb.append('<')
                "gt" -> sb.append('>')
                "quot" -> sb.append('"')
                "apos" -> sb.append('\'')
                else -> {
                    val code = when {
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.drop(2).toIntOrNull(16)
                        entity.startsWith("#") -> entity.drop(1).toIntOrNull()
                        else -> null
                    }
                    if (code != null) sb.appendCodePoint(code) else sb.append(s, i, end + 1)
                }
            }
            i = end + 1
        }
        return sb.toString()
    }

    // ------------------------------------------------------------- date cells

    /**
     * Excel stores dates as days since 1899-12-30, with the fractional part
     * being the time of day. Values are read in UTC and then rebuilt in the
     * device's zone, so a date does not slide a day either way.
     */
    fun excelSerialToMillis(serial: Double): Long {
        val utcDays = serial - 25569.0            // 25569 = 1970-01-01 in Excel serial
        val utcMillis = Math.round(utcDays * 86_400_000.0)

        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                utc.get(Calendar.HOUR_OF_DAY), utc.get(Calendar.MINUTE), utc.get(Calendar.SECOND)
            )
        }.timeInMillis
    }
}
