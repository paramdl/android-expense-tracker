package com.expensetracker.ui

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Amount formatting shared by every screen. */
object Fmt {

    private val whole = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val grouped2 = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    /**
     * Whole amounts stay clean ("₹1,250"); anything with paise shows both
     * decimals ("₹780.50") rather than a lone trailing digit.
     */
    private fun grouped(amount: Double): String {
        val hasPaise = kotlin.math.abs(amount - Math.round(amount)) > 0.004
        return if (hasPaise) grouped2.format(amount) else whole.format(amount)
    }

    /** 1234.5 -> "₹1,234.50", 1234.0 -> "₹1,234" */
    fun money(symbol: String, amount: Double): String = symbol + grouped(amount)

    /** Always two decimals, for exports and detail rows. */
    fun money2(symbol: String, amount: Double): String = symbol + grouped2.format(amount)

    /** Signed for ledger rows: "+₹500" / "-₹500". */
    fun signed(symbol: String, amount: Double, isIncome: Boolean): String =
        (if (isIncome) "+" else "-") + symbol + grouped(amount)

    /**
     * Compact axis labels using Indian units: 1_50_000 -> "1.5L", 25_00_000 -> "25L".
     */
    fun compact(amount: Double): String {
        val a = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            a >= 1_00_00_000 -> sign + trim(a / 1_00_00_000) + "Cr"
            a >= 1_00_000 -> sign + trim(a / 1_00_000) + "L"
            a >= 1_000 -> sign + trim(a / 1_000) + "K"
            else -> sign + trim(a)
        }
    }

    private fun trim(v: Double): String {
        val bd = v.toBigDecimal().setScale(1, RoundingMode.HALF_UP)
        val s = bd.toPlainString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    fun percent(part: Double, whole: Double): String {
        if (whole <= 0.0) return "0%"
        val p = part / whole * 100.0
        return if (p >= 10) p.toInt().toString() + "%" else String.format(Locale.US, "%.1f%%", p)
    }
}
