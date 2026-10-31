package com.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.R
import com.expensetracker.data.DateRanges
import com.expensetracker.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home screen widget: this month's spend and balance, with a one-tap route into
 * the entry screen.
 *
 * Reads the same database the app does, so it stays correct without any sync.
 * Refreshed on the system's own schedule and whenever the app writes.
 */
class SummaryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val app = context.applicationContext as? ExpenseApp ?: return

        // Database work must not run on the main thread, so draw the frame first
        // and fill in the figures once they are read.
        CoroutineScope(Dispatchers.IO).launch {
            val bounds = DateRanges.monthBounds(System.currentTimeMillis())
            val totals = runCatching { app.repo.totals(bounds.first, bounds.second) }.getOrNull()
            val currency = app.settings.currencySymbol

            val views = RemoteViews(context.packageName, R.layout.widget_summary).apply {
                setTextViewText(R.id.widget_month, DateRanges.monthLabel(System.currentTimeMillis()))
                if (totals == null) {
                    setTextViewText(R.id.widget_spent, "—")
                    setTextViewText(R.id.widget_balance, "")
                } else {
                    setTextViewText(R.id.widget_spent, Fmt.money(currency, totals.expense))
                    setTextViewText(
                        R.id.widget_balance,
                        (if (totals.balance >= 0) "+" else "") + Fmt.money(currency, totals.balance) + " left"
                    )
                }

                setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
                setOnClickPendingIntent(R.id.widget_add, openApp(context, MainActivity.ACTION_QUICK_ADD))
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun openApp(context: Context, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (action != null) this.action = action
        }
        return PendingIntent.getActivity(
            context,
            if (action == null) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Redraws every placed widget; called after the ledger changes. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SummaryWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, SummaryWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
