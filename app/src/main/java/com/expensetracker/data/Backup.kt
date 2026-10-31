package com.expensetracker.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-database backup as a single JSON document, plus CSV export.
 *
 * The JSON file is portable: it can be written to internal storage, handed to
 * the system share sheet (Google Drive, Gmail, WhatsApp) or saved straight into
 * a Drive folder picked through the Storage Access Framework.
 */
object Backup {

    const val FORMAT_VERSION = 1
    const val MIME_JSON = "application/json"
    const val MIME_CSV = "text/csv"

    data class RestoreResult(
        val categories: Int,
        val people: Int,
        val accounts: Int,
        val transactions: Int,
        val error: String? = null,
        val recurring: Int = 0
    ) {
        val ok get() = error == null
    }

    // ------------------------------------------------------------------ write

    fun defaultFileName(): String = "expense-backup-" + DateRanges.fileStamp() + ".json"

    fun defaultCsvName(): String = "expenses-" + DateRanges.fileStamp() + ".csv"

    fun csvFileName(from: Long, to: Long): String =
        "expenses-" + DateRanges.fileStamp(from).take(8) + "-to-" + DateRanges.fileStamp(to).take(8) + ".csv"

    fun buildJson(repo: Repo, settings: Settings): String {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("app", "Expense Tracker")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedAtReadable", DateRanges.iso(System.currentTimeMillis()))
        root.put("currency", settings.currencySymbol)
        root.put("monthlyBudget", settings.monthlyBudget)

        val cats = JSONArray()
        repo.categories(includeArchived = true).forEach { c ->
            cats.put(
                JSONObject()
                    .put("id", c.id).put("name", c.name).put("type", c.type.name)
                    .put("emoji", c.emoji).put("color", c.colorArgb).put("archived", c.archived)
            )
        }
        root.put("categories", cats)

        val ppl = JSONArray()
        repo.people(includeArchived = true).forEach { p ->
            ppl.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("note", p.note).put("archived", p.archived)
            )
        }
        root.put("people", ppl)

        val accs = JSONArray()
        repo.accounts(includeArchived = true).forEach { a ->
            accs.put(
                JSONObject().put("id", a.id).put("name", a.name).put("emoji", a.emoji)
                    .put("opening", a.openingBalance).put("archived", a.archived)
            )
        }
        root.put("accounts", accs)

        val rules = JSONArray()
        repo.recurringRules().forEach { r ->
            rules.put(
                JSONObject()
                    .put("id", r.id).put("name", r.name).put("type", r.type.name)
                    .put("amount", r.amount)
                    .put("categoryId", r.categoryId ?: JSONObject.NULL)
                    .put("personId", r.personId ?: JSONObject.NULL)
                    .put("accountId", r.accountId ?: JSONObject.NULL)
                    .put("toAccountId", r.toAccountId ?: JSONObject.NULL)
                    .put("merchant", r.merchant).put("note", r.note)
                    .put("frequency", r.frequency.name).put("intervalCount", r.intervalCount)
                    .put("startDate", r.startDate).put("endMode", r.endMode.name)
                    .put("endDate", r.endDate ?: JSONObject.NULL)
                    .put("totalCount", r.totalCount ?: JSONObject.NULL)
                    .put("generatedCount", r.generatedCount)
                    .put("active", r.active).put("autoPost", r.autoPost)
                    .put("createdAt", r.createdAt)
            )
        }
        root.put("recurring", rules)

        val txs = JSONArray()
        repo.transactions().forEach { v ->
            val t = v.tx
            txs.put(
                JSONObject()
                    .put("id", t.id).put("type", t.type.name).put("amount", t.amount)
                    .put("date", t.date)
                    .put("categoryId", t.categoryId ?: JSONObject.NULL)
                    .put("personId", t.personId ?: JSONObject.NULL)
                    .put("accountId", t.accountId ?: JSONObject.NULL)
                    .put("merchant", t.merchant).put("note", t.note)
                    .put("source", t.source.name)
                    .put("recurringId", t.recurringId ?: JSONObject.NULL)
                    .put("createdAt", t.createdAt)
            )
        }
        root.put("transactions", txs)
        return root.toString(2)
    }

    fun buildCsv(repo: Repo, from: Long, to: Long, currency: String): String {
        val sb = StringBuilder()
        sb.append("Date,Time,Type,Amount (").append(currency).append("),Category,Person,Account,Merchant,Note,Source\n")
        repo.transactions(from = from, to = to).forEach { v ->
            val t = v.tx
            sb.append(esc(DateRanges.dayFullLabel(t.date))).append(',')
                .append(esc(DateRanges.timeOfDay(t.date))).append(',')
                .append(t.type.name).append(',')
                .append(String.format("%.2f", t.amount)).append(',')
                .append(esc(v.categoryName ?: "")).append(',')
                .append(esc(v.personName ?: "")).append(',')
                .append(esc(v.accountName ?: "")).append(',')
                .append(esc(t.merchant)).append(',')
                .append(esc(t.note)).append(',')
                .append(t.source.name).append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val body = s.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ')
        return if (needsQuote) "\"" + body + "\"" else body
    }

    fun writeToUri(ctx: Context, uri: Uri, content: String): Boolean = try {
        // "wt" truncates, so re-saving over an existing file does not leave a tail.
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } != null
    } catch (e: Exception) {
        false
    }

    fun readFromUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    /** Writes a timestamped backup into a SAF folder the user granted access to. */
    fun writeToFolder(ctx: Context, folderUri: Uri, name: String, content: String, mime: String): String? {
        return try {
            val dir = DocumentFile.fromTreeUri(ctx, folderUri) ?: return null
            if (!dir.canWrite()) return null
            dir.findFile(name)?.delete()
            val file = dir.createFile(mime, name) ?: return null
            if (writeToUri(ctx, file.uri, content)) name else null
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * Restores a backup. When [replace] is true the existing ledger is cleared
     * first; otherwise the backup is merged on top of what is already there.
     *
     * Lookup rows are matched by name so a merge never creates duplicate
     * categories, and transactions are remapped onto the resulting ids.
     */
    fun restore(repo: Repo, json: String, replace: Boolean): RestoreResult {
        return try {
            val root = JSONObject(json)
            if (!root.has("transactions")) {
                return RestoreResult(0, 0, 0, 0, "This file is not an Expense Tracker backup.")
            }

            var nCat = 0
            var nPpl = 0
            var nAcc = 0
            var nTx = 0
            var nRules = 0
            val catMap = HashMap<Long, Long>()
            val pplMap = HashMap<Long, Long>()
            val accMap = HashMap<Long, Long>()

            repo.inTransaction {
                if (replace) repo.wipeTransactions()


                val existingCats = repo.categories(true)
                root.optJSONArray("categories")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val type = TxType.valueOf(o.optString("type", "EXPENSE"))
                        val match = existingCats.firstOrNull { it.name.equals(name, true) && it.type == type }
                        val id = match?.id ?: repo.upsertCategory(
                            Category(
                                name = name, type = type,
                                emoji = o.optString("emoji", "📁"),
                                colorArgb = o.optInt("color", 0xFF6B7280.toInt()),
                                archived = o.optBoolean("archived", false)
                            )
                        ).also { nCat++ }
                        catMap[o.optLong("id")] = id
                    }
                }

                val existingPpl = repo.people(true)
                root.optJSONArray("people")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val match = existingPpl.firstOrNull { it.name.equals(name, true) }
                        val id = match?.id ?: repo.upsertPerson(
                            Person(
                                name = name, note = o.optString("note", ""),
                                archived = o.optBoolean("archived", false)
                            )
                        ).also { nPpl++ }
                        pplMap[o.optLong("id")] = id
                    }
                }

                val existingAcc = repo.accounts(true)
                root.optJSONArray("accounts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val match = existingAcc.firstOrNull { it.name.equals(name, true) }
                        val id = match?.id ?: repo.upsertAccount(
                            Account(
                                name = name, emoji = o.optString("emoji", "🏦"),
                                openingBalance = o.optDouble("opening", 0.0),
                                archived = o.optBoolean("archived", false)
                            )
                        ).also { nAcc++ }
                        accMap[o.optLong("id")] = id
                    }
                }

                // Rules are restored before transactions so ids can be remapped.
                val ruleMap = HashMap<Long, Long>()
                val existingRules = repo.recurringRules()
                root.optJSONArray("recurring")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val match = existingRules.firstOrNull { it.name.equals(name, true) }
                        val id = match?.id ?: repo.upsertRecurring(
                            RecurringRule(
                                name = name,
                                type = TxType.valueOf(o.optString("type", "EXPENSE")),
                                amount = o.optDouble("amount", 0.0),
                                categoryId = mapId(o, "categoryId", catMap),
                                personId = mapId(o, "personId", pplMap),
                                accountId = mapId(o, "accountId", accMap),
                                toAccountId = mapId(o, "toAccountId", accMap),
                                merchant = o.optString("merchant", ""),
                                note = o.optString("note", ""),
                                frequency = runCatching {
                                    Frequency.valueOf(o.optString("frequency", "MONTHLY"))
                                }.getOrDefault(Frequency.MONTHLY),
                                intervalCount = o.optInt("intervalCount", 1),
                                startDate = o.optLong("startDate", System.currentTimeMillis()),
                                endMode = runCatching {
                                    EndMode.valueOf(o.optString("endMode", "NEVER"))
                                }.getOrDefault(EndMode.NEVER),
                                endDate = if (o.isNull("endDate")) null else o.optLong("endDate"),
                                totalCount = if (o.isNull("totalCount")) null else o.optInt("totalCount"),
                                generatedCount = o.optInt("generatedCount", 0),
                                active = o.optBoolean("active", true),
                                autoPost = o.optBoolean("autoPost", true),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        ruleMap[o.optLong("id")] = id
                        nRules++
                    }
                }

                val arr = root.getJSONArray("transactions")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    repo.upsertTx(
                        Transaction(
                            type = TxType.valueOf(o.optString("type", "EXPENSE")),
                            amount = o.optDouble("amount", 0.0),
                            date = o.optLong("date", System.currentTimeMillis()),
                            categoryId = mapId(o, "categoryId", catMap),
                            personId = mapId(o, "personId", pplMap),
                            accountId = mapId(o, "accountId", accMap),
                            merchant = o.optString("merchant", ""),
                            note = o.optString("note", ""),
                            source = runCatching { TxSource.valueOf(o.optString("source", "MANUAL")) }
                                .getOrDefault(TxSource.MANUAL),
                            recurringId = mapId(o, "recurringId", ruleMap),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                    nTx++
                }
            }
            RestoreResult(nCat, nPpl, nAcc, nTx, recurring = nRules)
        } catch (e: Exception) {
            RestoreResult(0, 0, 0, 0, e.message ?: "Could not read that file.")
        }
    }

    private fun mapId(o: JSONObject, key: String, map: Map<Long, Long>): Long? {
        if (o.isNull(key)) return null
        val old = o.optLong(key, 0L)
        if (old == 0L) return null
        return map[old]
    }
}
