package com.expensetracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        // WAL lets the dashboard keep reading while a write is in flight, which
        // matters most during SMS imports and restores.
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous=NORMAL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE category(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                emoji TEXT NOT NULL DEFAULT '📁',
                color INTEGER NOT NULL,
                budget REAL NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE person(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                archived INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE account(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL DEFAULT '🏦',
                opening REAL NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE tx(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                date INTEGER NOT NULL,
                category_id INTEGER REFERENCES category(id) ON DELETE SET NULL,
                person_id INTEGER REFERENCES person(id) ON DELETE SET NULL,
                account_id INTEGER REFERENCES account(id) ON DELETE SET NULL,
                merchant TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'MANUAL',
                recurring_id INTEGER,
                to_account_id INTEGER REFERENCES account(id) ON DELETE SET NULL,
                mode_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                method_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                bank_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                created_at INTEGER NOT NULL)"""
        )
        createIndices(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_recurring ON tx(recurring_id)")
        db.execSQL(
            """CREATE TABLE pending_sms(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                merchant TEXT NOT NULL DEFAULT '',
                account_hint TEXT NOT NULL DEFAULT '',
                ref_hash TEXT NOT NULL UNIQUE,
                status TEXT NOT NULL DEFAULT 'PENDING')"""
        )
        db.execSQL(RECURRING_TABLE)
        db.execSQL(PAYTAG_TABLE)
        seed(db)
        seedPayTags(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Steps run in ascending order so a database from any older version is
        // brought forward one release at a time.
        if (oldVersion < 2) {
            // v1 shipped with only (date) and (type); every dashboard aggregate
            // filters on type+date and groups by a foreign key.
            db.execSQL("DROP INDEX IF EXISTS idx_tx_date")
            db.execSQL("DROP INDEX IF EXISTS idx_tx_type")
            createIndices(db)
        }
        if (oldVersion < 3) {
            // Recurring rules, plus a back-reference from generated transactions.
            db.execSQL(RECURRING_TABLE)
            if (!hasColumn(db, "tx", "recurring_id")) {
                db.execSQL("ALTER TABLE tx ADD COLUMN recurring_id INTEGER")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_recurring ON tx(recurring_id)")
        }
        if (oldVersion < 4) {
            // Per-category budgets, and the destination account for transfers.
            if (!hasColumn(db, "category", "budget")) {
                db.execSQL("ALTER TABLE category ADD COLUMN budget REAL NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "tx", "to_account_id")) {
                db.execSQL("ALTER TABLE tx ADD COLUMN to_account_id INTEGER")
            }
        }
        if (oldVersion < 5) {
            // Payment mode / method / bank, each a user-extendable list.
            db.execSQL(PAYTAG_TABLE)
            for (col in listOf("mode_id", "method_id", "bank_id")) {
                if (!hasColumn(db, "tx", col)) {
                    db.execSQL("ALTER TABLE tx ADD COLUMN " + col + " INTEGER")
                }
            }
            if (db.rawQuery("SELECT 1 FROM paytag LIMIT 1", null).use { !it.moveToFirst() }) {
                seedPayTags(db)
            }
        }
        if (oldVersion < 6) {
            // A recurring rule can now fund a savings account (a transfer) and can
            // carry the payment mode / method / bank that its postings inherit.
            for (col in listOf("to_account_id", "mode_id", "method_id", "bank_id")) {
                if (!hasColumn(db, "recurring", col)) {
                    db.execSQL("ALTER TABLE recurring ADD COLUMN " + col + " INTEGER")
                }
            }
        }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info(" + table + ")", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(nameIdx) == column) return true
            false
        }

    private fun createIndices(db: SQLiteDatabase) {
        // Covers totals, byCategory, byAccount and topMerchants, which all
        // filter "type = ? AND date BETWEEN ? AND ?".
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_type_date ON tx(type, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_date ON tx(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_category ON tx(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_person ON tx(person_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_account ON tx(account_id)")
        // idx_tx_recurring is deliberately NOT here: this helper also runs in the
        // v1 -> v2 step, before the recurring_id column has been added.
    }

    private fun seedPayTags(db: SQLiteDatabase) {
        fun add(kind: String, name: String) =
            db.execSQL("INSERT INTO paytag(kind,name) VALUES(?,?)", arrayOf<Any>(kind, name))
        DEFAULT_MODES.forEach { add("MODE", it) }
        DEFAULT_METHODS.forEach { add("METHOD", it) }
        DEFAULT_BANKS.forEach { add("BANK", it) }
    }

    private fun seed(db: SQLiteDatabase) {
        fun cat(name: String, type: TxType, emoji: String, color: Long) {
            db.execSQL(
                "INSERT INTO category(name,type,emoji,color) VALUES(?,?,?,?)",
                arrayOf<Any>(name, type.name, emoji, color.toInt())
            )
        }
        DEFAULT_EXPENSE_CATEGORIES.forEach { cat(it.first, TxType.EXPENSE, it.second, it.third) }
        DEFAULT_INCOME_CATEGORIES.forEach { cat(it.first, TxType.INCOME, it.second, it.third) }

        DEFAULT_ACCOUNTS.forEach { (name, emoji) ->
            db.execSQL("INSERT INTO account(name,emoji) VALUES(?,?)", arrayOf<Any>(name, emoji))
        }
    }

    companion object {
        const val DB_NAME = "expense.db"
        const val DB_VERSION = 6

        const val RECURRING_TABLE = """CREATE TABLE IF NOT EXISTS recurring(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                category_id INTEGER REFERENCES category(id) ON DELETE SET NULL,
                person_id INTEGER REFERENCES person(id) ON DELETE SET NULL,
                account_id INTEGER REFERENCES account(id) ON DELETE SET NULL,
                to_account_id INTEGER REFERENCES account(id) ON DELETE SET NULL,
                mode_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                method_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                bank_id INTEGER REFERENCES paytag(id) ON DELETE SET NULL,
                merchant TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                frequency TEXT NOT NULL,
                interval_count INTEGER NOT NULL DEFAULT 1,
                start_date INTEGER NOT NULL,
                end_mode TEXT NOT NULL DEFAULT 'NEVER',
                end_date INTEGER,
                total_count INTEGER,
                generated_count INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                auto_post INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL)"""

        const val PAYTAG_TABLE = """CREATE TABLE IF NOT EXISTS paytag(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                name TEXT NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0)"""

        /** How the money moved. */
        val DEFAULT_MODES = listOf("Cash", "Online", "Card", "Cheque", "Auto-debit")

        /** The instrument used. */
        val DEFAULT_METHODS = listOf(
            "UPI", "Credit Card", "Debit Card", "Net Banking", "Wallet",
            "NEFT / IMPS", "Cash", "Cheque", "Standing Instruction"
        )

        /** Common Indian banks; the list is editable in the app. */
        val DEFAULT_BANKS = listOf(
            "HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank",
            "Kotak Mahindra", "Punjab National Bank", "Bank of Baroda",
            "Canara Bank", "Yes Bank", "IDFC First", "IndusInd Bank", "Union Bank"
        )

        val DEFAULT_EXPENSE_CATEGORIES = listOf(
            Triple("Shopping - Personal", "🛍️", 0xFFE06C9F),
            Triple("Shopping - Home", "🏠", 0xFF7C6BD6),
            Triple("Groceries", "🛒", 0xFF3FA372),
            Triple("Food & Dining", "🍽️", 0xFFE0783C),
            Triple("Fuel & Transport", "⛽", 0xFF3D8FD1),
            Triple("Bills & Utilities", "💡", 0xFFD1A73D),
            Triple("Rent", "🏡", 0xFF8C6E4F),
            Triple("Health & Medical", "🏥", 0xFFD64550),
            Triple("Education", "🎓", 0xFF4C6EDB),
            Triple("Entertainment", "🎬", 0xFFB05CD6),
            Triple("Travel", "✈️", 0xFF2FA8A8),
            Triple("Clothing", "👕", 0xFFD6707C),
            Triple("Personal Care", "💇", 0xFFCB6FB0),
            Triple("Recharge & Subscriptions", "📱", 0xFF5B8DEF),
            Triple("EMI & Loan", "💳", 0xFF9E5C5C),
            Triple("Insurance", "🛡️", 0xFF5C7A9E),
            Triple("Investment", "📈", 0xFF2E8B57),
            Triple("Gifts & Donations", "🎁", 0xFFD98BA0),
            Triple("Maintenance & Repairs", "🔧", 0xFF6E7E8A),
            Triple("Pets", "🐾", 0xFFA0793D),
            Triple("Lent to Person", "🤝", 0xFF7A8B3D),
            Triple("Other Expense", "💸", 0xFF6B7280)
        )

        val DEFAULT_INCOME_CATEGORIES = listOf(
            Triple("Salary", "💼", 0xFF12805C),
            Triple("Freelance / Business", "🧾", 0xFF2E7DA8),
            Triple("Rental Income", "🏠", 0xFF7C6BD6),
            Triple("Investment Returns", "📈", 0xFF3FA372),
            Triple("Interest", "💰", 0xFFD1A73D),
            Triple("Gift Received", "🎁", 0xFFD98BA0),
            Triple("Refund / Cashback", "↩️", 0xFF5B8DEF),
            Triple("Money Received Back", "🤝", 0xFF7A8B3D),
            Triple("Other Income", "💵", 0xFF6B7280)
        )

        val DEFAULT_ACCOUNTS = listOf(
            "Cash" to "💵",
            "Bank Account" to "🏦",
            "Credit Card" to "💳",
            "Debit Card" to "🪪",
            "UPI / Wallet" to "📲"
        )
    }
}
