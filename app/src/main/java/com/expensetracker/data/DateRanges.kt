package com.expensetracker.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar maths for the month / week / custom range pickers.
 *
 * Both [Calendar] and [SimpleDateFormat] are mutable and not thread-safe, and
 * building them is expensive enough to matter when a query buckets thousands of
 * rows by day. Everything here is therefore held per-thread and reused, which
 * also makes these helpers safe to call from the IO dispatcher and the UI at the
 * same time.
 */
object DateRanges {

    // Two separate instances: `scratch` is used by the public range helpers,
    // `dayCal` only inside startOfDay. Sharing one would let a nested call
    // silently re-point the calendar its caller is still holding.
    private val scratchCal = ThreadLocal.withInitial { Calendar.getInstance() }
    private val dayCal = ThreadLocal.withInitial { Calendar.getInstance() }

    private fun scratch(t: Long): Calendar =
        scratchCal.get()!!.apply { timeInMillis = t }

    private fun fmt(pattern: String, locale: Locale = Locale.getDefault()) =
        ThreadLocal.withInitial { SimpleDateFormat(pattern, locale) }

    private val monthTitle = fmt("MMMM yyyy")
    private val shortMonth = fmt("MMM")
    private val dayLabel = fmt("d MMM")
    private val dayFull = fmt("EEE, d MMM yyyy")
    private val timeLabel = fmt("h:mm a")
    private val stampFmt = fmt("yyyyMMdd-HHmmss", Locale.US)
    private val isoFmt = fmt("yyyy-MM-dd HH:mm", Locale.US)

    // -------------------------------------------------------- day boundaries

    /**
     * Start of the day containing [t].
     *
     * Hot path: called once per row when bucketing daily spending. The common
     * case is answered by arithmetic against a cached day window, and the
     * calendar is only consulted when [t] falls outside it — so a run of rows
     * from the same day costs two comparisons each.
     */
    fun startOfDay(t: Long): Long {
        val cache = dayCache.get()!!
        if (t >= cache.start && t < cache.end) return cache.start

        val c = dayCal.get()!!.apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        cache.start = start
        cache.end = c.timeInMillis
        return start
    }

    /** Start of the day after the one containing [t]; DST-safe. */
    fun nextDay(t: Long): Long {
        startOfDay(t)
        return dayCache.get()!!.end
    }

    fun endOfDay(t: Long): Long = nextDay(t) - 1

    private class DayWindow(var start: Long = 1L, var end: Long = 0L)

    private val dayCache = ThreadLocal.withInitial { DayWindow() }

    // ------------------------------------------------------------- ranges

    /** First and last millisecond of the month containing [t]. */
    fun monthBounds(t: Long): Pair<Long, Long> {
        val c = scratch(t)
        c.set(Calendar.DAY_OF_MONTH, 1)
        val start = startOfDay(c.timeInMillis)
        c.timeInMillis = start
        c.add(Calendar.MONTH, 1)
        return start to (startOfDay(c.timeInMillis) - 1)
    }

    fun yearBounds(t: Long): Pair<Long, Long> {
        val c = scratch(t)
        c.set(Calendar.DAY_OF_YEAR, 1)
        val start = startOfDay(c.timeInMillis)
        c.timeInMillis = start
        c.add(Calendar.YEAR, 1)
        return start to (startOfDay(c.timeInMillis) - 1)
    }

    fun weekBounds(t: Long): Pair<Long, Long> {
        val c = scratch(t)
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        val start = startOfDay(c.timeInMillis)
        c.timeInMillis = start
        c.add(Calendar.DAY_OF_YEAR, 7)
        return start to (startOfDay(c.timeInMillis) - 1)
    }

    fun addMonths(t: Long, delta: Int): Long = scratch(t).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, delta)
    }.timeInMillis

    fun daysAgo(days: Int): Long = startOfDay(System.currentTimeMillis() - days * 86_400_000L)

    fun isSameDay(a: Long, b: Long) = startOfDay(a) == startOfDay(b)

    /** Calendar year of [t], used to group the monthly trend query. */
    fun yearOf(t: Long): Int = scratch(t).get(Calendar.YEAR)

    /** Zero-based month of [t]. */
    fun monthOf(t: Long): Int = scratch(t).get(Calendar.MONTH)

    /** Milliseconds at the first instant of the given year/month. */
    fun monthStart(year: Int, monthZeroBased: Int): Long = scratch(0L).apply {
        clear()
        set(year, monthZeroBased, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ------------------------------------------------------------- labels

    fun monthLabel(t: Long): String = monthTitle.get()!!.format(Date(t))
    fun shortMonthLabel(t: Long): String = shortMonth.get()!!.format(Date(t))
    fun dayShort(t: Long): String = dayLabel.get()!!.format(Date(t))
    fun dayFullLabel(t: Long): String = dayFull.get()!!.format(Date(t))
    fun timeOfDay(t: Long): String = timeLabel.get()!!.format(Date(t))
    fun fileStamp(t: Long = System.currentTimeMillis()): String = stampFmt.get()!!.format(Date(t))
    fun iso(t: Long): String = isoFmt.get()!!.format(Date(t))

    /** "Today" / "Yesterday" / "Mon, 4 Aug 2026" for list headers. */
    fun relativeDay(t: Long): String {
        val now = System.currentTimeMillis()
        return when {
            isSameDay(t, now) -> "Today"
            isSameDay(t, now - 86_400_000L) -> "Yesterday"
            else -> dayFullLabel(t)
        }
    }
}
