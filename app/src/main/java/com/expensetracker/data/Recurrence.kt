package com.expensetracker.data

import java.util.Calendar

enum class Frequency { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }

enum class EndMode {
    /** Runs until paused or deleted — a subscription. */
    NEVER,

    /** Stops after a chosen calendar date. */
    ON_DATE,

    /** Stops after a fixed number of payments — a loan or EMI. */
    AFTER_N
}

/**
 * A repeating income, expense or savings contribution: an EMI, a monthly
 * subscription, an annual renewal, rent, a salary credit, a chit fund or
 * recurring deposit.
 *
 * A savings rule is a [TxType.TRANSFER] from the funding account to the pot in
 * [toAccountId], so it moves both balances without counting as spending.
 */
data class RecurringRule(
    val id: Long = 0,
    val name: String,
    val type: TxType,
    val amount: Double,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    /**
     * Where the money lands when [type] is [TxType.TRANSFER]: the savings pot,
     * chit fund or recurring deposit being funded. Null for income and expense.
     */
    val toAccountId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    /** How the money moves each time, mirroring the same fields on a transaction. */
    val modeId: Long? = null,
    val methodId: Long? = null,
    val bankId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    /** Repeat every N periods: 2 + MONTHLY means every other month. */
    val intervalCount: Int = 1,
    val startDate: Long,
    val endMode: EndMode = EndMode.NEVER,
    val endDate: Long? = null,
    /** Total payments for [EndMode.AFTER_N] — 36 for a three-year EMI. */
    val totalCount: Int? = null,
    /** How many occurrences have already been written to the ledger. */
    val generatedCount: Int = 0,
    val active: Boolean = true,
    /** When false the rule only shows as upcoming and is never posted on its own. */
    val autoPost: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Payments still to come, or null when the rule never ends. */
    val remaining: Int?
        get() = totalCount?.let { (it - generatedCount).coerceAtLeast(0) }
}

/**
 * Date maths for [RecurringRule].
 *
 * Every occurrence is computed as an offset from [RecurringRule.startDate]
 * rather than by stepping forward from the previous one. That matters for
 * monthly rules on the 29th–31st: stepping would drag the date earlier and
 * never recover (31 Jan → 28 Feb → 28 Mar), whereas offsetting from the start
 * clamps only where it must (31 Jan → 28 Feb → 31 Mar).
 */
object Recurrence {

    /** Hard ceiling on rows a single catch-up may create, as a runaway guard. */
    const val MAX_CATCH_UP = 240

    /** Timestamp of the [index]-th occurrence, counting the start as index 0. */
    fun occurrenceAt(rule: RecurringRule, index: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = rule.startDate
        val step = rule.intervalCount.coerceAtLeast(1) * index

        when (rule.frequency) {
            Frequency.DAILY -> c.add(Calendar.DAY_OF_YEAR, step)
            Frequency.WEEKLY -> c.add(Calendar.DAY_OF_YEAR, step * 7)
            Frequency.MONTHLY -> addMonthsClamped(c, step)
            Frequency.QUARTERLY -> addMonthsClamped(c, step * 3)
            Frequency.YEARLY -> addMonthsClamped(c, step * 12)
        }
        return c.timeInMillis
    }

    /**
     * Adds whole months while keeping the original day of month where the target
     * month is long enough, and clamping to the last day where it is not.
     */
    private fun addMonthsClamped(c: Calendar, months: Int) {
        if (months == 0) return
        val day = c.get(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, months)
        c.set(Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
    }

    /** True when [index] is past whatever end condition the rule carries. */
    fun isFinished(rule: RecurringRule, index: Int): Boolean = when (rule.endMode) {
        EndMode.NEVER -> false
        EndMode.AFTER_N -> index >= (rule.totalCount ?: 0)
        EndMode.ON_DATE -> {
            val end = rule.endDate
            end != null && occurrenceAt(rule, index) > end
        }
    }

    /** When the next unposted occurrence falls, or null once the rule is done. */
    fun nextDue(rule: RecurringRule): Long? {
        if (isFinished(rule, rule.generatedCount)) return null
        return occurrenceAt(rule, rule.generatedCount)
    }

    /**
     * Occurrences that have come due at or before [now] and are not yet posted.
     *
     * Returns them oldest first, so reopening the app after a long gap fills in
     * the months that were missed rather than only the latest one.
     */
    fun dueOccurrences(rule: RecurringRule, now: Long): List<Long> {
        if (!rule.active) return emptyList()
        val out = ArrayList<Long>()
        var index = rule.generatedCount
        while (out.size < MAX_CATCH_UP) {
            if (isFinished(rule, index)) break
            val at = occurrenceAt(rule, index)
            if (at > now) break
            out.add(at)
            index++
        }
        return out
    }

    /** Human label for the schedule, e.g. "Every 2 months" or "Yearly". */
    fun describe(rule: RecurringRule): String {
        val n = rule.intervalCount.coerceAtLeast(1)
        val unit = when (rule.frequency) {
            Frequency.DAILY -> if (n == 1) "day" else "days"
            Frequency.WEEKLY -> if (n == 1) "week" else "weeks"
            Frequency.MONTHLY -> if (n == 1) "month" else "months"
            Frequency.QUARTERLY -> if (n == 1) "quarter" else "quarters"
            Frequency.YEARLY -> if (n == 1) "year" else "years"
        }
        return if (n == 1) "Every " + unit else "Every " + n + " " + unit
    }

    /** Short label for the end condition, for the rule list. */
    fun describeEnd(rule: RecurringRule): String = when (rule.endMode) {
        EndMode.NEVER -> "No end date"
        EndMode.ON_DATE -> rule.endDate?.let { "Until " + DateRanges.dayShort(it) } ?: "No end date"
        EndMode.AFTER_N -> {
            val total = rule.totalCount ?: 0
            rule.generatedCount.coerceAtMost(total).toString() + " of " + total + " paid"
        }
    }
}
