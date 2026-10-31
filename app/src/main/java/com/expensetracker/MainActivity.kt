package com.expensetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.expensetracker.ui.AppRoot
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.theme.ExpenseTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleSharedText(intent)
        handleQuickAction(intent)

        setContent {
            val theme by vm.theme.collectAsState()
            ExpenseTheme(themeMode = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
        handleQuickAction(intent)
    }

    /** Widget button and launcher shortcuts land here. */
    private fun handleQuickAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_ADD -> vm.setQuickAction(AppViewModel.Quick.ADD)
            ACTION_QUICK_IMPORT -> vm.setQuickAction(AppViewModel.Quick.IMPORT)
        }
    }

    /** Picks up a bank message shared in from the user's SMS or mail app. */
    private fun handleSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrEmpty()) vm.setSharedText(text)
    }

    override fun onStart() {
        super.onStart()
        // The ViewModel's flow pipelines already load on creation and re-run on
        // every write, so re-reading here would just duplicate the first pass.
        // Fills in any EMI/subscription payments that fell due while the app was closed.
        vm.postDueRecurring()
        vm.maybeAutoBackup()
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.expensetracker.QUICK_ADD"
        const val ACTION_QUICK_IMPORT = "com.expensetracker.QUICK_IMPORT"
    }
}
