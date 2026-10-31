package com.expensetracker.data

/**
 * Tiny arithmetic evaluator for the amount field, so a bill can be entered as
 * `450+120*2` or `1200/3` without leaving the app for a calculator.
 *
 * Hand-written recursive descent over `+ - * / ( )` and decimals. Deliberately
 * not a general expression engine: anything it does not recognise returns null
 * and the field simply shows nothing rather than guessing at a number.
 */
object Calc {

    private val DIGIT_GAP = Regex("""[0-9.]\s+[0-9.]""")

    /** True when [text] is more than a plain number and worth evaluating. */
    fun isExpression(text: String): Boolean =
        text.any { it == '+' || it == '*' || it == '/' || it == '(' } ||
            // A minus only counts as an operator when it is not a leading sign.
            text.indexOf('-') > 0

    /**
     * Evaluates [text], or returns null when it is empty, malformed, or the
     * result is not a usable amount (negative, infinite, NaN).
     */
    fun eval(text: String): Double? {
        // Two numbers separated only by whitespace are two separate figures, not
        // one. Stripping the space first would silently turn "450 120" into
        // 450120, so reject that shape before normalising.
        if (DIGIT_GAP.containsMatchIn(text)) return null

        val src = text.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (src.isEmpty()) return null
        return try {
            val p = Parser(src)
            val v = p.expression()
            if (!p.atEnd()) return null
            if (v.isNaN() || v.isInfinite() || v < 0) null else v
        } catch (e: Exception) {
            null
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        private fun peek(): Char? = if (i < s.length) s[i] else null

        /** expression := term (('+' | '-') term)* */
        fun expression(): Double {
            var value = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; value += term() }
                    '-' -> { i++; value -= term() }
                    else -> return value
                }
            }
        }

        /** term := factor (('*' | '/') factor)* */
        private fun term(): Double {
            var value = factor()
            while (true) {
                when (peek()) {
                    '*' -> { i++; value *= factor() }
                    '/' -> {
                        i++
                        val d = factor()
                        if (d == 0.0) throw ArithmeticException("divide by zero")
                        value /= d
                    }
                    else -> return value
                }
            }
        }

        /** factor := ['-'] ( number | '(' expression ')' ) */
        private fun factor(): Double {
            if (peek() == '-') { i++; return -factor() }
            if (peek() == '(') {
                i++
                val v = expression()
                if (peek() != ')') throw IllegalStateException("unclosed bracket")
                i++
                return v
            }
            return number()
        }

        private fun number(): Double {
            val start = i
            var seenDot = false
            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !seenDot))) {
                if (s[i] == '.') seenDot = true
                i++
            }
            if (i == start) throw IllegalStateException("expected a number")
            return s.substring(start, i).toDouble()
        }
    }
}
