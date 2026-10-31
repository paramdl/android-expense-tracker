package com.expensetracker.data

import android.util.Log
import com.expensetracker.BuildConfig

/**
 * Lightweight timing for the data layer. Compiled to no-ops in release: the
 * lambda is inlined and the whole body sits behind a constant `false`, so the
 * optimiser drops it.
 */
object Perf {

    private const val TAG = "ExpensePerf"

    inline fun <T> trace(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val start = System.nanoTime()
        val result = block()
        report(label, (System.nanoTime() - start) / 1_000_000.0)
        return result
    }

    fun report(label: String, millis: Double) {
        if (BuildConfig.DEBUG) Log.d(TAG, label + " " + String.format("%.1f", millis) + " ms")
    }
}
