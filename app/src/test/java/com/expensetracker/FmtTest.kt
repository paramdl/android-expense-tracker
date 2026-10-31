package com.expensetracker

import com.expensetracker.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @Test
    fun `whole amounts render without decimals`() {
        assertEquals("₹1,250", Fmt.money("₹", 1250.0))
        assertEquals("₹0", Fmt.money("₹", 0.0))
    }

    @Test
    fun `paise always render as two digits`() {
        assertEquals("₹780.50", Fmt.money("₹", 780.5))
        assertEquals("₹1,299.75", Fmt.money("₹", 1299.75))
    }

    @Test
    fun `signed amounts carry the right sign`() {
        assertEquals("+₹15,000", Fmt.signed("₹", 15000.0, true))
        assertEquals("-₹780.50", Fmt.signed("₹", 780.5, false))
    }

    @Test
    fun `compact uses indian units`() {
        assertEquals("1.5L", Fmt.compact(150000.0))
        assertEquals("25K", Fmt.compact(25000.0))
        assertEquals("1.2Cr", Fmt.compact(12000000.0))
        assertEquals("450", Fmt.compact(450.0))
    }

    @Test
    fun `percent handles a zero total`() {
        assertEquals("0%", Fmt.percent(50.0, 0.0))
        assertEquals("25%", Fmt.percent(25.0, 100.0))
    }
}
