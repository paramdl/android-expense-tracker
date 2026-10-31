package com.expensetracker

import com.expensetracker.data.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The amount field feeds straight into money, so bad input must yield null, not a wrong number. */
class CalcTest {

    private fun eval(s: String) = Calc.eval(s)

    @Test
    fun `plain numbers evaluate to themselves`() {
        assertEquals(450.0, eval("450")!!, 0.0001)
        assertEquals(1299.50, eval("1299.50")!!, 0.0001)
        assertEquals(0.0, eval("0")!!, 0.0001)
    }

    @Test
    fun `addition and subtraction`() {
        assertEquals(570.0, eval("450+120")!!, 0.0001)
        assertEquals(330.0, eval("450-120")!!, 0.0001)
        assertEquals(600.0, eval("100+200+300")!!, 0.0001)
    }

    @Test
    fun `multiplication and division bind tighter than addition`() {
        assertEquals(690.0, eval("450+120*2")!!, 0.0001)
        assertEquals(400.0, eval("1200/3")!!, 0.0001)
        assertEquals(250.0, eval("100+300/2")!!, 0.0001)
    }

    @Test
    fun `brackets override precedence`() {
        assertEquals(1140.0, eval("(450+120)*2")!!, 0.0001)
        assertEquals(200.0, eval("(300+100)/2")!!, 0.0001)
    }

    @Test
    fun `splitting a bill three ways`() {
        assertEquals(416.67, eval("1250/3")!!, 0.01)
    }

    @Test
    fun `decimals inside an expression`() {
        assertEquals(1450.25, eval("1299.50+150.75")!!, 0.0001)
    }

    @Test
    fun `spaces are ignored`() {
        assertEquals(570.0, eval("450 + 120")!!, 0.0001)
    }

    @Test
    fun `unicode multiply and divide signs are accepted`() {
        assertEquals(900.0, eval("450×2")!!, 0.0001)
        assertEquals(225.0, eval("450÷2")!!, 0.0001)
    }

    // ------------------------------------------------------------- rejections

    @Test
    fun `malformed input returns null rather than a guess`() {
        assertNull(eval("450+"))
        assertNull(eval("450++120"))
        assertNull(eval("(450+120"))
        assertNull(eval("450)"))
        assertNull(eval("abc"))
        assertNull(eval("450 120"))
        assertNull(eval(""))
    }

    @Test
    fun `divide by zero returns null`() {
        assertNull(eval("450/0"))
    }

    @Test
    fun `a negative result is refused because an amount cannot be negative`() {
        assertNull(eval("120-450"))
    }

    @Test
    fun `trailing operator mid-typing does not crash`() {
        // The field evaluates on every keystroke, so half-typed input is normal.
        assertNull(eval("450*"))
        assertNull(eval("450/"))
        assertNull(eval("-"))
    }

    // ------------------------------------------------------- expression check

    @Test
    fun `isExpression distinguishes maths from a plain amount`() {
        assertTrue(Calc.isExpression("450+120"))
        assertTrue(Calc.isExpression("1250/3"))
        assertTrue(Calc.isExpression("(450)"))
        assertTrue(Calc.isExpression("450-120"))
        assertFalse(Calc.isExpression("450"))
        assertFalse(Calc.isExpression("1299.50"))
        // A leading minus is a sign, not an operator, so it is not an expression.
        assertFalse(Calc.isExpression("-450"))
    }
}
