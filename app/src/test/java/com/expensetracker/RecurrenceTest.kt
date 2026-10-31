package com.expensetracker

import com.expensetracker.data.EndMode
import com.expensetracker.data.Frequency
import com.expensetracker.data.Recurrence
import com.expensetracker.data.RecurringRule
import com.expensetracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Schedule arithmetic decides what lands in the ledger without the user
 * looking, so month-end clamping and end conditions are worth pinning down.
 */
class RecurrenceTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 9): Long =
        Calendar.getInstance().apply {
            clear(); set(y, m - 1, d, h, 0, 0)
        }.timeInMillis

    private fun ymd(t: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun rule(
        start: Long,
        freq: Frequency = Frequency.MONTHLY,
        interval: Int = 1,
        endMode: EndMode = EndMode.NEVER,
        endDate: Long? = null,
        total: Int? = null,
        generated: Int = 0
    ) = RecurringRule(
        name = "Test", type = TxType.EXPENSE, amount = 100.0, startDate = start,
        frequency = freq, intervalCount = interval, endMode = endMode,
        endDate = endDate, totalCount = total, generatedCount = generated
    )

    // ------------------------------------------------------ month-end clamping

    @Test
    fun `monthly on the 31st clamps to short months without drifting`() {
        val r = rule(at(2026, 1, 31))
        assertEquals(Triple(2026, 1, 31), ymd(Recurrence.occurrenceAt(r, 0)))
        assertEquals(Triple(2026, 2, 28), ymd(Recurrence.occurrenceAt(r, 1)))
        // The critical one: March must return to the 31st, not stay on the 28th.
        assertEquals(Triple(2026, 3, 31), ymd(Recurrence.occurrenceAt(r, 2)))
        assertEquals(Triple(2026, 4, 30), ymd(Recurrence.occurrenceAt(r, 3)))
        assertEquals(Triple(2026, 5, 31), ymd(Recurrence.occurrenceAt(r, 4)))
    }

    @Test
    fun `monthly on the 29th handles a leap February`() {
        val r = rule(at(2027, 1, 29))
        assertEquals(Triple(2027, 2, 28), ymd(Recurrence.occurrenceAt(r, 1)))
        val leap = rule(at(2028, 1, 29))
        assertEquals(Triple(2028, 2, 29), ymd(Recurrence.occurrenceAt(leap, 1)))
    }

    @Test
    fun `yearly from 29 February clamps in non-leap years`() {
        val r = rule(at(2028, 2, 29), Frequency.YEARLY)
        assertEquals(Triple(2028, 2, 29), ymd(Recurrence.occurrenceAt(r, 0)))
        assertEquals(Triple(2029, 2, 28), ymd(Recurrence.occurrenceAt(r, 1)))
        assertEquals(Triple(2032, 2, 29), ymd(Recurrence.occurrenceAt(r, 4)))
    }

    @Test
    fun `time of day is preserved across occurrences`() {
        val r = rule(at(2026, 3, 15, h = 18))
        val c = Calendar.getInstance().apply { timeInMillis = Recurrence.occurrenceAt(r, 5) }
        assertEquals(18, c.get(Calendar.HOUR_OF_DAY))
    }

    // ----------------------------------------------------------- frequencies

    @Test
    fun `daily weekly quarterly and yearly step correctly`() {
        assertEquals(
            Triple(2026, 8, 31),
            ymd(Recurrence.occurrenceAt(rule(at(2026, 8, 24), Frequency.DAILY), 7))
        )
        assertEquals(
            Triple(2026, 9, 14),
            ymd(Recurrence.occurrenceAt(rule(at(2026, 8, 24), Frequency.WEEKLY), 3))
        )
        assertEquals(
            Triple(2027, 2, 24),
            ymd(Recurrence.occurrenceAt(rule(at(2026, 8, 24), Frequency.QUARTERLY), 2))
        )
        assertEquals(
            Triple(2029, 8, 24),
            ymd(Recurrence.occurrenceAt(rule(at(2026, 8, 24), Frequency.YEARLY), 3))
        )
    }

    @Test
    fun `interval count multiplies the period`() {
        val everyOtherMonth = rule(at(2026, 1, 10), Frequency.MONTHLY, interval = 2)
        assertEquals(Triple(2026, 3, 10), ymd(Recurrence.occurrenceAt(everyOtherMonth, 1)))
        assertEquals(Triple(2026, 7, 10), ymd(Recurrence.occurrenceAt(everyOtherMonth, 3)))
    }

    // --------------------------------------------------------- end conditions

    @Test
    fun `emi of 36 payments stops after the last one`() {
        val emi = rule(at(2026, 1, 5), endMode = EndMode.AFTER_N, total = 36, generated = 35)
        assertTrue(!Recurrence.isFinished(emi, 35))
        assertTrue(Recurrence.isFinished(emi, 36))

        val done = emi.copy(generatedCount = 36)
        assertNull(Recurrence.nextDue(done))
        assertEquals(0, Recurrence.dueOccurrences(done, at(2030, 1, 1)).size)
    }

    @Test
    fun `end on date stops once occurrences pass it`() {
        val r = rule(at(2026, 1, 15), endMode = EndMode.ON_DATE, endDate = at(2026, 4, 30))
        // Jan, Feb, Mar, Apr are inside the window; May is past it.
        assertTrue(!Recurrence.isFinished(r, 3))
        assertTrue(Recurrence.isFinished(r, 4))
    }

    @Test
    fun `subscription with no end never finishes`() {
        val r = rule(at(2026, 1, 1))
        assertTrue(!Recurrence.isFinished(r, 500))
    }

    // ------------------------------------------------------------- catch-up

    @Test
    fun `reopening after three idle months backfills every missed occurrence`() {
        val r = rule(at(2026, 5, 10))
        val due = Recurrence.dueOccurrences(r, at(2026, 8, 12))
        // May, June, July, August
        assertEquals(4, due.size)
        assertEquals(Triple(2026, 5, 10), ymd(due.first()))
        assertEquals(Triple(2026, 8, 10), ymd(due.last()))
    }

    @Test
    fun `an occurrence later today is not posted early`() {
        val r = rule(at(2026, 8, 25, h = 23))
        assertEquals(0, Recurrence.dueOccurrences(r, at(2026, 8, 25, h = 9)).size)
    }

    @Test
    fun `already posted occurrences are not repeated`() {
        val r = rule(at(2026, 5, 10), generated = 2)
        val due = Recurrence.dueOccurrences(r, at(2026, 8, 12))
        assertEquals(2, due.size)
        assertEquals(Triple(2026, 7, 10), ymd(due.first()))
    }

    @Test
    fun `a paused rule posts nothing`() {
        val r = rule(at(2026, 1, 1)).copy(active = false)
        assertEquals(0, Recurrence.dueOccurrences(r, at(2026, 8, 1)).size)
    }

    @Test
    fun `catch-up is capped so a very old daily rule cannot flood the ledger`() {
        val r = rule(at(2000, 1, 1), Frequency.DAILY)
        assertEquals(Recurrence.MAX_CATCH_UP, Recurrence.dueOccurrences(r, at(2026, 8, 1)).size)
    }

    @Test
    fun `end by count is respected during catch-up`() {
        val r = rule(at(2026, 1, 10), endMode = EndMode.AFTER_N, total = 3)
        assertEquals(3, Recurrence.dueOccurrences(r, at(2027, 1, 1)).size)
    }

    // ------------------------------------------------------------- labels

    @Test
    fun `an open-ended savings rule keeps its destination and backfills every month`() {
        // A chit fund joined three months ago: no end date, and the months that
        // passed before the rule was entered still belong in the ledger.
        val start = at(2026, 6, 5)
        val rule = RecurringRule(
            name = "Chit fund",
            type = TxType.TRANSFER,
            amount = 10_000.0,
            accountId = 1L,
            toAccountId = 2L,
            modeId = 7L,
            startDate = start,
            frequency = Frequency.MONTHLY,
            endMode = EndMode.NEVER
        )

        val due = Recurrence.dueOccurrences(rule, at(2026, 9, 7))
        assertEquals(4, due.size)
        assertEquals(Triple(2026, 6, 5), ymd(due[0]))
        assertEquals(Triple(2026, 9, 5), ymd(due[3]))

        // Nothing about a savings rule ends it, however many runs it has made.
        val after = rule.copy(generatedCount = 4)
        assertEquals(Triple(2026, 10, 5), ymd(Recurrence.nextDue(after)!!))
        assertEquals("No end date", Recurrence.describeEnd(after))

        // The transfer wiring survives the copy the poster works with.
        assertEquals(2L, after.toAccountId)
        assertEquals(7L, after.modeId)
    }

    @Test
    fun `schedule labels read naturally`() {
        assertEquals("Every month", Recurrence.describe(rule(at(2026, 1, 1))))
        assertEquals(
            "Every 2 months",
            Recurrence.describe(rule(at(2026, 1, 1), Frequency.MONTHLY, interval = 2))
        )
        assertEquals("Every year", Recurrence.describe(rule(at(2026, 1, 1), Frequency.YEARLY)))
    }

    @Test
    fun `emi progress label counts payments made`() {
        val emi = rule(at(2026, 1, 5), endMode = EndMode.AFTER_N, total = 36, generated = 12)
        assertEquals("12 of 36 paid", Recurrence.describeEnd(emi))
        assertEquals(24, emi.remaining)
    }
}
