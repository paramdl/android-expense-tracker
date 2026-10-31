package com.expensetracker

import com.expensetracker.data.TxType
import com.expensetracker.data.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser decides what reaches the ledger, so both directions matter:
 * real alerts must be caught, and everything else must be left alone.
 */
class SmsParserTest {

    private val now = 1_756_000_000_000L

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, now)

    // ------------------------------------------------------------- debits

    @Test
    fun `hdfc upi debit is parsed with amount and merchant`() {
        val p = parse(
            "AD-HDFCBK",
            "Rs.450.00 debited from a/c XXXX1234 on 20-08-26 to VPA swiggy@ybl. Ref no 123456789012. Not you? Call 18001234567"
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(450.0, p.amount, 0.001)
        assertEquals("swiggy@ybl", p.merchant)
        assertEquals("A/c ****1234", p.accountHint)
    }

    @Test
    fun `icici card spend is parsed`() {
        val p = parse(
            "VM-ICICIB",
            "INR 1,299.50 spent on ICICI Bank Card XX1234 at AMAZON on 21-Aug-26. Avl Lmt: INR 48,700.50"
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(1299.50, p.amount, 0.001)
        assertEquals("Card ****4567", p.accountHint)
    }

    @Test
    fun `sbi debit with trailing balance does not pick up the balance figure`() {
        val p = parse(
            "JD-SBIINB",
            "Dear Customer, Rs 2000.00 debited from A/c no XX1234 on 22Aug26. Avl Bal Rs 45678.90. -SBI"
        )
        assertNotNull(p)
        assertEquals(2000.0, p!!.amount, 0.001)
    }

    @Test
    fun `amount written before the currency word is still found`() {
        val p = parse("AX-AXISBK", "Spent 350 Rs on card XX1234 at BIGBASKET")
        assertNotNull(p)
        assertEquals(350.0, p!!.amount, 0.001)
    }

    // ------------------------------------------------------------ credits

    @Test
    fun `salary credit is parsed as income`() {
        val p = parse(
            "AD-KOTAKB",
            "Rs 85,000.00 credited to your A/c XX1234 on 01-Aug-26 towards SALARY AUG. Avl Bal Rs 91,204.10"
        )
        assertNotNull(p)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(85000.0, p.amount, 0.001)
        assertEquals("Salary", SmsParser.guessCategory(p.merchant, p.body, p.type))
    }

    @Test
    fun `refund is income and categorised as refund`() {
        val p = parse("VK-PAYTMB", "Refund of Rs 599 received in your Paytm Wallet for order 8891")
        assertNotNull(p)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals("Refund / Cashback", SmsParser.guessCategory(p.merchant, p.body, p.type))
    }

    // ------------------------------------------------------------ rejects

    @Test
    fun `otp messages are never treated as transactions`() {
        assertNull(parse("VM-HDFCBK", "123456 is your OTP for a transaction of Rs 5000. Do not share."))
    }

    @Test
    fun `future dated reminders are ignored`() {
        assertNull(parse("AD-ICICIB", "Rs 4500 will be debited from your account on 25-Aug for SIP"))
    }

    @Test
    fun `failed transactions are ignored`() {
        assertNull(parse("AD-HDFCBK", "Your payment of Rs 200 to Zomato has failed. Amount will be refunded."))
    }

    @Test
    fun `collect requests are ignored`() {
        assertNull(parse("VM-PHONEP", "Rakesh has requested Rs 500. Approve in PhonePe to pay."))
    }

    @Test
    fun `promotional offers are ignored`() {
        assertNull(parse("AD-MYNTRA", "Flat Rs 500 discount! Sale is live. Shop now and save big."))
    }

    @Test
    fun `balance only alerts are ignored`() {
        assertNull(parse("AD-SBIINB", "Your balance in A/c XX1234 is Rs 45678.90 as on 22Aug26"))
    }

    @Test
    fun `personal message from a phone number is ignored`() {
        assertNull(parse("9876543210", "hey I paid 500 for the movie, send it back"))
    }

    @Test
    fun `messages with no amount are ignored`() {
        assertNull(parse("AD-HDFCBK", "Your account has been debited. Check the app for details."))
    }

    // ------------------------------------------------------- deduplication

    @Test
    fun `same reference number yields the same hash`() {
        val a = parse("AD-HDFCBK", "Rs.450 debited to VPA swiggy@ybl. Ref no 123456789012.")
        val b = parse("AD-HDFCBK", "Rs.450 debited to VPA swiggy@ybl. Ref no 123456789012.")
        assertNotNull(a); assertNotNull(b)
        assertEquals(a!!.refHash, b!!.refHash)
    }

    @Test
    fun `different transactions produce different hashes`() {
        val a = parse("AD-HDFCBK", "Rs.450 debited to VPA swiggy@ybl. Ref no 123456789012.")
        val b = parse("AD-HDFCBK", "Rs.870 debited to VPA zomato@ybl. Ref no 123456789012.")
        assertTrue(a!!.refHash != b!!.refHash)
    }

    // ------------------------------------------------------- categorisation

    @Test
    fun `known merchants map to the seeded categories`() {
        assertEquals("Food & Dining", SmsParser.guessCategory("swiggy@ybl", "", TxType.EXPENSE))
        assertEquals("Groceries", SmsParser.guessCategory("BIGBASKET", "", TxType.EXPENSE))
        assertEquals("Fuel & Transport", SmsParser.guessCategory("UBER INDIA", "", TxType.EXPENSE))
        assertEquals("Shopping - Personal", SmsParser.guessCategory("AMAZON PAY", "", TxType.EXPENSE))
        assertEquals("Recharge & Subscriptions", SmsParser.guessCategory("NETFLIX", "", TxType.EXPENSE))
    }

    @Test
    fun `freelance credit is categorised, not left blank`() {
        val p = parse(
            "AD-KOTAKB",
            "Rs 15000.00 credited to your A/c XX1234 on 24-Aug-26 towards FREELANCE PAYMENT. Avl Bal Rs 91204.10"
        )
        assertNotNull(p)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(15000.0, p.amount, 0.001)
        assertEquals("Freelance / Business", SmsParser.guessCategory(p.merchant, p.body, p.type))
    }

    @Test
    fun `unknown merchants stay uncategorised`() {
        assertNull(SmsParser.guessCategory("QWERTY TRADERS", "some random text", TxType.EXPENSE))
    }

    // -------------------------------------------------------- amount edges

    @Test
    fun `comma grouped amounts are parsed correctly`() {
        assertEquals(1_25_000.0, SmsParser.extractAmount("Rs 1,25,000.00 credited")!!, 0.001)
        assertEquals(1250.75, SmsParser.extractAmount("INR 1,250.75 debited")!!, 0.001)
        assertEquals(45.0, SmsParser.extractAmount("₹45 paid")!!, 0.001)
    }

    // ------------------------------------------------- user-supplied messages

    @Test
    fun `pasted message parses even though it has no bank sender`() {
        val p = SmsParser.parseShared(
            "Rs.450.00 debited from a/c XXXX1234 on 20-08-26 to VPA swiggy@ybl. Ref no 123456789012.",
            now
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(450.0, p.amount, 0.001)
        assertEquals("swiggy@ybl", p.merchant)
    }

    @Test
    fun `shared message still rejects an otp`() {
        assertNull(SmsParser.parseShared("123456 is your OTP for a transaction of Rs 5000.", now))
    }

    @Test
    fun `shared message still rejects a future reminder`() {
        assertNull(SmsParser.parseShared("Rs 4500 will be debited on 25-Aug for SIP", now))
    }

    @Test
    fun `shared message rejects text with no amount`() {
        assertNull(SmsParser.parseShared("your account has been debited, check the app", now))
    }

    @Test
    fun `shared and inbox parsing agree on the same message`() {
        val body = "INR 1,299.50 spent on ICICI Bank Card XX1234 at AMAZON on 21-Aug-26."
        val viaInbox = SmsParser.parse("VM-ICICIB", body, now)
        val viaShare = SmsParser.parseShared(body, now)
        assertNotNull(viaInbox); assertNotNull(viaShare)
        assertEquals(viaInbox!!.amount, viaShare!!.amount, 0.001)
        assertEquals(viaInbox.type, viaShare.type)
        assertEquals(viaInbox.accountHint, viaShare.accountHint)
    }
}
