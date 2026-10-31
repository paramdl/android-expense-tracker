package com.expensetracker.data

import android.content.Context

/** Thin typed wrapper over SharedPreferences. */
class Settings(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currencySymbol: String
        get() = sp.getString(K_CURRENCY, "₹") ?: "₹"
        set(v) = sp.edit().putString(K_CURRENCY, v).apply()

    /** OFF / SYSTEM / LIGHT / DARK */
    var themeMode: String
        get() = sp.getString(K_THEME, "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString(K_THEME, v).apply()

    /** Master switch for reading SMS at all. */
    var smsEnabled: Boolean
        get() = sp.getBoolean(K_SMS_ON, false)
        set(v) = sp.edit().putBoolean(K_SMS_ON, v).apply()

    /** When true a parsed SMS becomes a transaction immediately, no review step. */
    var smsAutoRecord: Boolean
        get() = sp.getBoolean(K_SMS_AUTO, false)
        set(v) = sp.edit().putBoolean(K_SMS_AUTO, v).apply()

    /** SAF tree uri (e.g. a Google Drive / local folder) for automatic backups. */
    var backupFolderUri: String?
        get() = sp.getString(K_BACKUP_DIR, null)
        set(v) = sp.edit().putString(K_BACKUP_DIR, v).apply()

    var autoBackupEnabled: Boolean
        get() = sp.getBoolean(K_AUTO_BACKUP, false)
        set(v) = sp.edit().putBoolean(K_AUTO_BACKUP, v).apply()

    var lastBackupAt: Long
        get() = sp.getLong(K_LAST_BACKUP, 0L)
        set(v) = sp.edit().putLong(K_LAST_BACKUP, v).apply()

    var lastBackupName: String
        get() = sp.getString(K_LAST_BACKUP_NAME, "") ?: ""
        set(v) = sp.edit().putString(K_LAST_BACKUP_NAME, v).apply()

    /** Monthly spending budget; 0 means no budget set. */
    var monthlyBudget: Double
        get() = sp.getFloat(K_BUDGET, 0f).toDouble()
        set(v) = sp.edit().putFloat(K_BUDGET, v.toFloat()).apply()

    /** Newest SMS timestamp already scanned, so inbox imports do not redo work. */
    var lastSmsScanAt: Long
        get() = sp.getLong(K_LAST_SCAN, 0L)
        set(v) = sp.edit().putLong(K_LAST_SCAN, v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean(K_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(K_ONBOARDED, v).apply()

    private companion object {
        const val K_CURRENCY = "currency"
        const val K_THEME = "theme"
        const val K_SMS_ON = "sms_enabled"
        const val K_SMS_AUTO = "sms_auto"
        const val K_BACKUP_DIR = "backup_dir"
        const val K_AUTO_BACKUP = "auto_backup"
        const val K_LAST_BACKUP = "last_backup"
        const val K_LAST_BACKUP_NAME = "last_backup_name"
        const val K_BUDGET = "budget"
        const val K_LAST_SCAN = "last_scan"
        const val K_ONBOARDED = "onboarded"
    }
}
