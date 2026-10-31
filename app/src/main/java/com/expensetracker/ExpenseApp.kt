package com.expensetracker

import android.app.Application
import com.expensetracker.data.Repo
import com.expensetracker.data.Settings

/** Process-wide singletons. Kept deliberately small: no DI framework needed. */
class ExpenseApp : Application() {

    val repo: Repo by lazy { Repo(this) }
    val settings: Settings by lazy { Settings(this) }
}
