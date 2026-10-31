package com.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar

/**
 * Single access point to the local database. Every mutating call bumps [version]
 * so the UI layer can re-run its queries.
 */
class Repo(ctx: Context) {

    private val helper = DbHelper(ctx.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    /** Incremented after every write; observed by the ViewModel to refresh. */
    val version = MutableStateFlow(0L)

    private var batchDepth = 0
    private var batchDirty = false

    private fun touch() {
        if (batchDepth > 0) {
            batchDirty = true
            return
        }
        version.value = version.value + 1
    }

    /**
     * Runs [block] with change notifications held back, emitting a single
     * version bump at the end. A restore or a bulk SMS accept would otherwise
     * kick off one full dashboard refresh per row.
     */
    fun <T> batch(block: () -> T): T {
        batchDepth++
        try {
            return block()
        } finally {
            batchDepth--
            if (batchDepth == 0 && batchDirty) {
                batchDirty = false
                version.value = version.value + 1
            }
        }
    }

    // ---------------------------------------------------------------- lookups

    fun categories(includeArchived: Boolean = false): List<Category> {
        val filter = if (includeArchived) "" else " WHERE archived=0"
        return db.rawQuery(
            "SELECT id,name,type,emoji,color,archived,budget FROM category$filter " +
                "ORDER BY type, name COLLATE NOCASE",
            null
        ).use { c ->
            c.map {
                Category(
                    id = it.getLong(0),
                    name = it.getString(1),
                    type = TxType.valueOf(it.getString(2)),
                    emoji = it.getString(3),
                    colorArgb = it.getInt(4),
                    archived = it.getInt(5) == 1,
                    budget = it.getDouble(6)
                )
            }
        }
    }

    fun people(includeArchived: Boolean = false): List<Person> {
        val filter = if (includeArchived) "" else " WHERE archived=0"
        return db.rawQuery(
            "SELECT id,name,note,archived FROM person$filter ORDER BY name COLLATE NOCASE", null
        ).use { c ->
            c.map { Person(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3) == 1) }
        }
    }

    fun accounts(includeArchived: Boolean = false): List<Account> {
        val filter = if (includeArchived) "" else " WHERE archived=0"
        return db.rawQuery(
            "SELECT id,name,emoji,opening,archived FROM account$filter ORDER BY name COLLATE NOCASE", null
        ).use { c ->
            c.map {
                Account(it.getLong(0), it.getString(1), it.getString(2), it.getDouble(3), it.getInt(4) == 1)
            }
        }
    }

    fun upsertCategory(c: Category): Long {
        val v = ContentValues().apply {
            put("name", c.name); put("type", c.type.name); put("emoji", c.emoji)
            put("color", c.colorArgb); put("archived", if (c.archived) 1 else 0)
            put("budget", c.budget)
        }
        val id = if (c.id == 0L) db.insert("category", null, v)
        else {
            db.update("category", v, "id=?", arrayOf(c.id.toString())); c.id
        }
        touch(); return id
    }

    fun upsertPerson(p: Person): Long {
        val v = ContentValues().apply {
            put("name", p.name); put("note", p.note); put("archived", if (p.archived) 1 else 0)
        }
        val id = if (p.id == 0L) db.insert("person", null, v)
        else {
            db.update("person", v, "id=?", arrayOf(p.id.toString())); p.id
        }
        touch(); return id
    }

    /** Finds a person by name (case-insensitive) or creates one. */
    fun personByNameOrCreate(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return 0L
        db.rawQuery("SELECT id FROM person WHERE name=? COLLATE NOCASE LIMIT 1", arrayOf(clean)).use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return upsertPerson(Person(name = clean))
    }

    fun upsertAccount(a: Account): Long {
        val v = ContentValues().apply {
            put("name", a.name); put("emoji", a.emoji); put("opening", a.openingBalance)
            put("archived", if (a.archived) 1 else 0)
        }
        val id = if (a.id == 0L) db.insert("account", null, v)
        else {
            db.update("account", v, "id=?", arrayOf(a.id.toString())); a.id
        }
        touch(); return id
    }

    fun deleteCategory(id: Long) {
        db.delete("category", "id=?", arrayOf(id.toString())); touch()
    }

    fun deletePerson(id: Long) {
        db.delete("person", "id=?", arrayOf(id.toString())); touch()
    }

    fun deleteAccount(id: Long) {
        db.delete("account", "id=?", arrayOf(id.toString())); touch()
    }

    // ------------------------------------------------------------- pay tags

    fun payTags(kind: PayKind): List<PayTag> =
        db.rawQuery(
            "SELECT id,kind,name,archived FROM paytag WHERE kind=? AND archived=0 " +
                "ORDER BY name COLLATE NOCASE", arrayOf(kind.name)
        ).use { c ->
            c.map { PayTag(it.getLong(0), PayKind.valueOf(it.getString(1)), it.getString(2), it.getInt(3) == 1) }
        }

    /** Adds a custom mode/method/bank, reusing an existing one of the same name. */
    fun payTagByNameOrCreate(kind: PayKind, name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return 0L
        db.rawQuery(
            "SELECT id FROM paytag WHERE kind=? AND name=? COLLATE NOCASE LIMIT 1",
            arrayOf(kind.name, clean)
        ).use { if (it.moveToFirst()) return it.getLong(0) }
        val v = ContentValues().apply { put("kind", kind.name); put("name", clean) }
        val id = db.insert("paytag", null, v)
        touch(); return id
    }

    fun deletePayTag(id: Long) {
        db.delete("paytag", "id=?", arrayOf(id.toString())); touch()
    }

    /** Spending grouped by payment method, for the reports screen. */
    fun byPaymentMethod(from: Long, to: Long): List<Bucket> =
        db.rawQuery(
            "SELECT COALESCE(m.name,'Not recorded'), SUM(t.amount), COUNT(*) " +
                "FROM tx t LEFT JOIN paytag m ON m.id=t.method_id " +
                "WHERE t.type='EXPENSE' AND t.date BETWEEN ? AND ? " +
                "GROUP BY t.method_id ORDER BY SUM(t.amount) DESC",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            c.map { Bucket(it.getString(0), it.getString(0), it.getDouble(1), count = it.getInt(2)) }
        }

    // ----------------------------------------------------------- transactions

    fun upsertTx(t: Transaction): Long {
        val v = ContentValues().apply {
            put("type", t.type.name); put("amount", t.amount); put("date", t.date)
            put("category_id", t.categoryId); put("person_id", t.personId)
            put("account_id", t.accountId); put("merchant", t.merchant); put("note", t.note)
            put("source", t.source.name); put("recurring_id", t.recurringId)
            put("to_account_id", t.toAccountId)
            put("mode_id", t.modeId); put("method_id", t.methodId); put("bank_id", t.bankId)
            put("created_at", t.createdAt)
        }
        val id = if (t.id == 0L) db.insert("tx", null, v)
        else {
            db.update("tx", v, "id=?", arrayOf(t.id.toString())); t.id
        }
        touch(); return id
    }

    fun deleteTx(id: Long) {
        db.delete("tx", "id=?", arrayOf(id.toString())); touch()
    }

    fun txById(id: Long): Transaction? =
        db.rawQuery(
            "SELECT id,type,amount,date,category_id,person_id,account_id,merchant,note,source," +
                "created_at,recurring_id,to_account_id,mode_id,method_id,bank_id " +
                "FROM tx WHERE id=?", arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) c.readTx() else null }

    /** Filtered transaction list. All filters are optional and combine with AND. */
    fun transactions(
        from: Long? = null,
        to: Long? = null,
        type: TxType? = null,
        categoryId: Long? = null,
        personId: Long? = null,
        accountId: Long? = null,
        query: String = "",
        limit: Int = 0
    ): List<TxView> {
        val where = StringBuilder("1=1")
        val args = ArrayList<String>()
        if (from != null) {
            where.append(" AND t.date>=?"); args.add(from.toString())
        }
        if (to != null) {
            where.append(" AND t.date<=?"); args.add(to.toString())
        }
        if (type != null) {
            where.append(" AND t.type=?"); args.add(type.name)
        }
        if (categoryId != null) {
            where.append(" AND t.category_id=?"); args.add(categoryId.toString())
        }
        if (personId != null) {
            where.append(" AND t.person_id=?"); args.add(personId.toString())
        }
        if (accountId != null) {
            where.append(" AND t.account_id=?"); args.add(accountId.toString())
        }
        if (query.isNotBlank()) {
            where.append(
                " AND (t.merchant LIKE ? OR t.note LIKE ? OR c.name LIKE ? OR p.name LIKE ? OR CAST(t.amount AS TEXT) LIKE ?)"
            )
            val q = "%" + query.trim() + "%"
            repeat(5) { args.add(q) }
        }
        val lim = if (limit > 0) " LIMIT $limit" else ""
        val sql = "SELECT t.id,t.type,t.amount,t.date,t.category_id,t.person_id,t.account_id," +
            "t.merchant,t.note,t.source,t.created_at,c.name,c.emoji,c.color,p.name,a.name,a.emoji," +
            "t.recurring_id,t.to_account_id,ta.name," +
            "t.mode_id,t.method_id,t.bank_id,pm.name,pb.name " +
            "FROM tx t " +
            "LEFT JOIN category c ON c.id=t.category_id " +
            "LEFT JOIN person p ON p.id=t.person_id " +
            "LEFT JOIN account a ON a.id=t.account_id " +
            "LEFT JOIN account ta ON ta.id=t.to_account_id " +
            "LEFT JOIN paytag pm ON pm.id=t.method_id " +
            "LEFT JOIN paytag pb ON pb.id=t.bank_id " +
            "WHERE $where ORDER BY t.date DESC, t.id DESC$lim"
        return db.rawQuery(sql, args.toTypedArray()).use { c -> c.map { it.readTxView() } }
    }

    // ------------------------------------------------------------- aggregates

    fun totals(from: Long, to: Long): Totals {
        var inc = 0.0
        var exp = 0.0
        db.rawQuery(
            "SELECT type, SUM(amount) FROM tx WHERE date BETWEEN ? AND ? " +
                "AND type IN ('INCOME','EXPENSE') GROUP BY type",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == TxType.INCOME.name) inc = c.getDouble(1) else exp = c.getDouble(1)
            }
        }
        return Totals(inc, exp)
    }

    fun byCategory(from: Long, to: Long, type: TxType): List<Bucket> {
        val fallback = GREY.toString()
        return db.rawQuery(
            "SELECT COALESCE(c.name,'Uncategorised'), COALESCE(c.emoji,'?'), COALESCE(c.color,$fallback), " +
                "SUM(t.amount), COUNT(*) FROM tx t LEFT JOIN category c ON c.id=t.category_id " +
                "WHERE t.type=? AND t.date BETWEEN ? AND ? " +
                "GROUP BY t.category_id ORDER BY SUM(t.amount) DESC",
            arrayOf(type.name, from.toString(), to.toString())
        ).use { c ->
            c.map {
                Bucket(
                    key = it.getString(0), label = it.getString(0), amount = it.getDouble(3),
                    colorArgb = it.getInt(2), emoji = it.getString(1), count = it.getInt(4)
                )
            }
        }
    }

    fun byPerson(from: Long, to: Long, type: TxType?): List<Bucket> {
        val args = arrayListOf(from.toString(), to.toString())
        val typeClause = if (type != null) {
            args.add(type.name); " AND t.type=?"
        } else ""
        return db.rawQuery(
            "SELECT p.name, SUM(t.amount), COUNT(*) FROM tx t JOIN person p ON p.id=t.person_id " +
                "WHERE t.date BETWEEN ? AND ?$typeClause GROUP BY p.id ORDER BY SUM(t.amount) DESC",
            args.toTypedArray()
        ).use { c ->
            c.map { Bucket(it.getString(0), it.getString(0), it.getDouble(1), count = it.getInt(2)) }
        }
    }

    fun byAccount(from: Long, to: Long, type: TxType): List<Bucket> =
        db.rawQuery(
            "SELECT COALESCE(a.name,'Unassigned'), COALESCE(a.emoji,'?'), SUM(t.amount), COUNT(*) " +
                "FROM tx t LEFT JOIN account a ON a.id=t.account_id " +
                "WHERE t.type=? AND t.date BETWEEN ? AND ? " +
                "GROUP BY t.account_id ORDER BY SUM(t.amount) DESC",
            arrayOf(type.name, from.toString(), to.toString())
        ).use { c ->
            c.map {
                Bucket(
                    it.getString(0), it.getString(0), it.getDouble(2),
                    emoji = it.getString(1), count = it.getInt(3)
                )
            }
        }

    fun topMerchants(from: Long, to: Long, limit: Int = 8): List<Bucket> =
        db.rawQuery(
            "SELECT merchant, SUM(amount), COUNT(*) FROM tx " +
                "WHERE type='EXPENSE' AND merchant<>'' AND date BETWEEN ? AND ? " +
                "GROUP BY merchant COLLATE NOCASE ORDER BY SUM(amount) DESC LIMIT ?",
            arrayOf(from.toString(), to.toString(), limit.toString())
        ).use { c ->
            c.map { Bucket(it.getString(0), it.getString(0), it.getDouble(1), count = it.getInt(2)) }
        }

    /**
     * Income/expense totals for the [months] most recent months ending with [anchor].
     *
     * Buckets in SQL over a single date range rather than issuing one query per
     * month, so the cost no longer scales with the number of months shown.
     */
    fun monthlyTrend(anchor: Long, months: Int): List<Triple<String, Double, Double>> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = anchor
        cal.add(Calendar.MONTH, -(months - 1))

        // Month starts for the whole window, oldest first.
        val starts = LongArray(months)
        for (i in 0 until months) {
            starts[i] = DateRanges.monthBounds(cal.timeInMillis).first
            cal.add(Calendar.MONTH, 1)
        }
        val windowStart = starts[0]
        val windowEnd = DateRanges.monthBounds(starts[months - 1]).second

        val income = DoubleArray(months)
        val expense = DoubleArray(months)

        db.rawQuery(
            "SELECT type, date, amount FROM tx WHERE date BETWEEN ? AND ? " +
                "AND type IN ('INCOME','EXPENSE')",
            arrayOf(windowStart.toString(), windowEnd.toString())
        ).use { c ->
            val isIncomeName = TxType.INCOME.name
            while (c.moveToNext()) {
                val date = c.getLong(1)
                // Months are ordered, so walk back from the end to find the bucket.
                var idx = months - 1
                while (idx > 0 && date < starts[idx]) idx--
                if (c.getString(0) == isIncomeName) income[idx] += c.getDouble(2)
                else expense[idx] += c.getDouble(2)
            }
        }

        val out = ArrayList<Triple<String, Double, Double>>(months)
        for (i in 0 until months) {
            out.add(Triple(DateRanges.shortMonthLabel(starts[i]), income[i], expense[i]))
        }
        return out
    }

    /** Daily expense totals across [from]..[to], one entry per day including empty days. */
    fun dailyExpense(from: Long, to: Long): List<Pair<Long, Double>> {
        val map = HashMap<Long, Double>()
        db.rawQuery(
            "SELECT date, amount FROM tx WHERE type='EXPENSE' AND date BETWEEN ? AND ?",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val day = DateRanges.startOfDay(c.getLong(0))
                map[day] = (map[day] ?: 0.0) + c.getDouble(1)
            }
        }
        val out = ArrayList<Pair<Long, Double>>(map.size + 32)
        var d = DateRanges.startOfDay(from)
        val end = DateRanges.startOfDay(to)
        var guard = 0
        while (d <= end && guard < 400) {
            out.add(d to (map[d] ?: 0.0))
            d = DateRanges.nextDay(d)
            guard++
        }
        return out
    }

    /** Running balance per account = opening + income - expense. */
    fun accountBalances(): List<Pair<Account, Double>> {
        val sums = HashMap<Long, Double>()
        db.rawQuery(
            "SELECT account_id, type, SUM(amount) FROM tx WHERE account_id IS NOT NULL GROUP BY account_id, type",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                // Income adds; expense and the outgoing leg of a transfer subtract.
                val delta = if (c.getString(1) == TxType.INCOME.name) c.getDouble(2) else -c.getDouble(2)
                sums[id] = (sums[id] ?: 0.0) + delta
            }
        }
        // Incoming leg of every transfer.
        db.rawQuery(
            "SELECT to_account_id, SUM(amount) FROM tx WHERE type='TRANSFER' " +
                "AND to_account_id IS NOT NULL GROUP BY to_account_id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                sums[id] = (sums[id] ?: 0.0) + c.getDouble(1)
            }
        }
        return accounts().map { it to it.openingBalance + (sums[it.id] ?: 0.0) }
    }

    fun earliestTxDate(): Long? =
        db.rawQuery("SELECT MIN(date) FROM tx", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    fun txCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM tx", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ------------------------------------------------------------ pending SMS

    fun addPendingSms(p: PendingSms): Boolean {
        val v = ContentValues().apply {
            put("sender", p.sender); put("body", p.body); put("received_at", p.receivedAt)
            put("amount", p.amount); put("type", p.type.name); put("merchant", p.merchant)
            put("account_hint", p.accountHint); put("ref_hash", p.refHash); put("status", p.status)
        }
        val id = db.insertWithOnConflict("pending_sms", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) touch()
        return id != -1L
    }

    fun pendingSms(status: String = "PENDING"): List<PendingSms> =
        db.rawQuery(
            "SELECT id,sender,body,received_at,amount,type,merchant,account_hint,ref_hash,status " +
                "FROM pending_sms WHERE status=? ORDER BY received_at DESC", arrayOf(status)
        ).use { c ->
            c.map {
                PendingSms(
                    it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getDouble(4),
                    TxType.valueOf(it.getString(5)), it.getString(6), it.getString(7),
                    it.getString(8), it.getString(9)
                )
            }
        }

    fun pendingSmsCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM pending_sms WHERE status='PENDING'", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun setSmsStatus(id: Long, status: String) {
        db.execSQL("UPDATE pending_sms SET status=? WHERE id=?", arrayOf<Any>(status, id))
        touch()
    }

    fun smsHashExists(hash: String): Boolean =
        db.rawQuery("SELECT 1 FROM pending_sms WHERE ref_hash=? LIMIT 1", arrayOf(hash))
            .use { it.moveToFirst() }

    fun clearProcessedSms() {
        db.execSQL("DELETE FROM pending_sms WHERE status<>'PENDING'")
        touch()
    }

    /**
     * Categories that carry a budget, paired with what has been spent against
     * them in the given window. Drives the budget list and its warnings.
     */
    fun categoryBudgets(from: Long, to: Long): List<CategoryBudget> {
        val spentByCategory = HashMap<Long, Double>()
        db.rawQuery(
            "SELECT category_id, SUM(amount) FROM tx WHERE type='EXPENSE' " +
                "AND category_id IS NOT NULL AND date BETWEEN ? AND ? GROUP BY category_id",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) spentByCategory[c.getLong(0)] = c.getDouble(1)
        }
        return categories()
            .filter { it.type == TxType.EXPENSE && it.budget > 0 }
            .map { CategoryBudget(it, spentByCategory[it.id] ?: 0.0) }
            .sortedByDescending { it.usedFraction }
    }

    /**
     * Merchants already used, most frequent first, for the entry field's
     * suggestions. Matching is a prefix/contains test on what has been typed.
     */
    fun merchantSuggestions(prefix: String, limit: Int = 6): List<String> {
        val like = "%" + prefix.trim() + "%"
        return db.rawQuery(
            "SELECT merchant, COUNT(*) c FROM tx WHERE merchant<>'' AND merchant LIKE ? " +
                "GROUP BY merchant COLLATE NOCASE ORDER BY c DESC, merchant LIMIT ?",
            arrayOf(like, limit.toString())
        ).use { c -> c.map { it.getString(0) } }
    }

    /** Re-inserts a deleted transaction, keeping its original id where free. */
    fun restoreTx(t: Transaction): Long = upsertTx(t.copy(id = 0))

    // ------------------------------------------------------------- recurring

    fun recurringRules(includeInactive: Boolean = true): List<RecurringRule> {
        val filter = if (includeInactive) "" else " WHERE active=1"
        return db.rawQuery(
            "SELECT id,name,type,amount,category_id,person_id,account_id,merchant,note," +
                "frequency,interval_count,start_date,end_mode,end_date,total_count," +
                "generated_count,active,auto_post,created_at," +
                "to_account_id,mode_id,method_id,bank_id FROM recurring$filter " +
                "ORDER BY active DESC, name COLLATE NOCASE", null
        ).use { c -> c.map { it.readRule() } }
    }

    fun upsertRecurring(r: RecurringRule): Long {
        val v = ContentValues().apply {
            put("name", r.name); put("type", r.type.name); put("amount", r.amount)
            put("category_id", r.categoryId); put("person_id", r.personId)
            put("account_id", r.accountId); put("to_account_id", r.toAccountId)
            put("mode_id", r.modeId); put("method_id", r.methodId); put("bank_id", r.bankId)
            put("merchant", r.merchant); put("note", r.note)
            put("frequency", r.frequency.name); put("interval_count", r.intervalCount)
            put("start_date", r.startDate); put("end_mode", r.endMode.name)
            put("end_date", r.endDate); put("total_count", r.totalCount)
            put("generated_count", r.generatedCount)
            put("active", if (r.active) 1 else 0)
            put("auto_post", if (r.autoPost) 1 else 0)
            put("created_at", r.createdAt)
        }
        val id = if (r.id == 0L) db.insert("recurring", null, v)
        else {
            db.update("recurring", v, "id=?", arrayOf(r.id.toString())); r.id
        }
        touch(); return id
    }

    fun deleteRecurring(id: Long, alsoDeletePosted: Boolean) {
        batch {
            if (alsoDeletePosted) {
                db.delete("tx", "recurring_id=?", arrayOf(id.toString()))
            } else {
                // Keep the history but detach it, so the rows survive the rule.
                db.execSQL("UPDATE tx SET recurring_id=NULL WHERE recurring_id=?", arrayOf<Any>(id))
            }
            db.delete("recurring", "id=?", arrayOf(id.toString()))
            touch()
        }
    }

    fun setRecurringActive(id: Long, active: Boolean) {
        db.execSQL(
            "UPDATE recurring SET active=? WHERE id=?",
            arrayOf<Any>(if (active) 1 else 0, id)
        )
        touch()
    }

    /**
     * Writes every occurrence that has come due into the ledger and advances each
     * rule's counter. Returns how many transactions were created.
     *
     * Safe to call on every app start: a rule only ever advances by the number of
     * occurrences actually written, so nothing is posted twice.
     */
    fun postDueRecurring(now: Long = System.currentTimeMillis()): Int {
        var posted = 0
        batch {
            recurringRules(includeInactive = false).forEach { rule ->
                if (!rule.autoPost) return@forEach
                val due = Recurrence.dueOccurrences(rule, now)
                if (due.isEmpty()) return@forEach
                due.forEach { at ->
                    db.insert("tx", null, ContentValues().apply {
                        put("type", rule.type.name); put("amount", rule.amount)
                        put("date", at)
                        put("category_id", rule.categoryId); put("person_id", rule.personId)
                        put("account_id", rule.accountId)
                        put("to_account_id", rule.toAccountId)
                        put("mode_id", rule.modeId); put("method_id", rule.methodId)
                        put("bank_id", rule.bankId)
                        put("merchant", rule.merchant.ifBlank { rule.name })
                        put("note", rule.note)
                        put("source", TxSource.RECURRING.name)
                        put("recurring_id", rule.id)
                        put("created_at", System.currentTimeMillis())
                    })
                    posted++
                }
                db.execSQL(
                    "UPDATE recurring SET generated_count=generated_count+? WHERE id=?",
                    arrayOf<Any>(due.size, rule.id)
                )
                // A finished rule stops being active so it leaves the upcoming list.
                val advanced = rule.copy(generatedCount = rule.generatedCount + due.size)
                if (Recurrence.nextDue(advanced) == null) {
                    db.execSQL("UPDATE recurring SET active=0 WHERE id=?", arrayOf<Any>(rule.id))
                }
            }
            if (posted > 0) touch()
        }
        return posted
    }

    /** Rules with an upcoming occurrence, soonest first. */
    fun upcomingRecurring(limit: Int = 5): List<Pair<RecurringRule, Long>> =
        recurringRules(includeInactive = false)
            .mapNotNull { r -> Recurrence.nextDue(r)?.let { r to it } }
            .sortedBy { it.second }
            .take(limit)

    // ------------------------------------------------------------------ bulk

    fun wipeTransactions() {
        db.execSQL("DELETE FROM tx")
        db.execSQL("DELETE FROM pending_sms")
        touch()
    }

    fun writableDb(): SQLiteDatabase = db

    fun inTransaction(block: () -> Unit) {
        batch {
            db.beginTransaction()
            try {
                block()
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            touch()
        }
    }

    fun notifyChanged() = touch()

    companion object {
        private val GREY = 0xFF9AA0A6.toInt()
    }
}

// ------------------------------------------------------------- cursor helpers

private inline fun <T> Cursor.map(body: (Cursor) -> T): List<T> {
    // Cursor.count is known up front, so the list never has to grow.
    val out = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) out.add(body(this))
    return out
}

private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)

private fun Cursor.readRule() = RecurringRule(
    id = getLong(0),
    name = getString(1),
    type = TxType.valueOf(getString(2)),
    amount = getDouble(3),
    categoryId = longOrNull(4),
    personId = longOrNull(5),
    accountId = longOrNull(6),
    merchant = getString(7),
    note = getString(8),
    frequency = Frequency.valueOf(getString(9)),
    intervalCount = getInt(10),
    startDate = getLong(11),
    endMode = EndMode.valueOf(getString(12)),
    endDate = longOrNull(13),
    totalCount = intOrNull(14),
    generatedCount = getInt(15),
    active = getInt(16) == 1,
    autoPost = getInt(17) == 1,
    createdAt = getLong(18),
    toAccountId = longOrNull(19),
    modeId = longOrNull(20),
    methodId = longOrNull(21),
    bankId = longOrNull(22)
)

private fun Cursor.readTx() = Transaction(
    id = getLong(0),
    type = TxType.valueOf(getString(1)),
    amount = getDouble(2),
    date = getLong(3),
    categoryId = longOrNull(4),
    personId = longOrNull(5),
    accountId = longOrNull(6),
    merchant = getString(7),
    note = getString(8),
    source = TxSource.valueOf(getString(9)),
    createdAt = getLong(10),
    recurringId = if (columnCount > 11) longOrNull(11) else null,
    toAccountId = if (columnCount > 12) longOrNull(12) else null,
    modeId = if (columnCount > 13) longOrNull(13) else null,
    methodId = if (columnCount > 14) longOrNull(14) else null,
    bankId = if (columnCount > 15) longOrNull(15) else null
)

private fun Cursor.readTxView() = TxView(
    tx = readTx().copy(
        recurringId = if (columnCount > 17) longOrNull(17) else null,
        toAccountId = if (columnCount > 18) longOrNull(18) else null,
        modeId = if (columnCount > 20) longOrNull(20) else null,
        methodId = if (columnCount > 21) longOrNull(21) else null,
        bankId = if (columnCount > 22) longOrNull(22) else null
    ),
    categoryName = if (isNull(11)) null else getString(11),
    categoryEmoji = if (isNull(12)) null else getString(12),
    categoryColor = if (isNull(13)) null else getInt(13),
    personName = if (isNull(14)) null else getString(14),
    accountName = if (isNull(15)) null else getString(15),
    accountEmoji = if (isNull(16)) null else getString(16),
    toAccountName = if (columnCount > 19 && !isNull(19)) getString(19) else null,
    methodName = if (columnCount > 23 && !isNull(23)) getString(23) else null,
    bankName = if (columnCount > 24 && !isNull(24)) getString(24) else null
)
