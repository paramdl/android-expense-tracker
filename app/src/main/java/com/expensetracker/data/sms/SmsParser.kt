package com.expensetracker.data.sms

import com.expensetracker.data.PendingSms
import com.expensetracker.data.TxType

/**
 * Rule based parser for Indian bank / UPI / card transaction alerts.
 *
 * The parser is deliberately conservative: if it cannot find both a clear
 * direction (debit vs credit) and a clear amount, it returns null rather than
 * guessing, so junk never reaches the ledger.
 */
object SmsParser {

    // ------------------------------------------------------------- exclusions

    /** Messages that mention money but are not a completed transaction. */
    private val BLOCKERS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "do not share", "password is", "login code",
        "will be debited", "will be deducted", "is due", "due on", "due date",
        "payment due", "please pay", "pay now to", "reminder",
        "failed", "declined", "unsuccessful", "reversed", "cancelled",
        "request for", "requesting", "has requested", "collect request",
        "available balance is", "avl bal is", "your balance",
        "offer", "cashback offer", "discount", "sale is live", "buy now",
        "apply now", "click here", "congratulations", "you have won",
        "loan approved", "pre-approved", "eligible for", "upgrade your",
        "insurance premium reminder", "statement is ready", "e-statement",
        "minimum amount due", "total amount due", "credit limit"
    )

    /** Words that mean money left the account. */
    private val DEBIT_WORDS = listOf(
        "debited", "debit", "spent", "paid", "payment of", "withdrawn",
        "purchase of", "purchased", "deducted", "transferred to", "trf to",
        "sent to", "sent rs", "charged", "txn of", "debited for", "used for"
    )

    /** Words that mean money came in. */
    private val CREDIT_WORDS = listOf(
        "credited", "credit of", "received", "deposited", "refund",
        "refunded", "cashback of", "salary", "has been credited",
        "added to", "reversal of", "reimbursed"
    )

    // ---------------------------------------------------------------- regexes

    private val AMOUNT_AFTER = Regex(
        """(?:rs\.?|inr|₹|mrp)\s*[:\s]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_BEFORE = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|₹)""",
        RegexOption.IGNORE_CASE
    )

    private val VPA = Regex("""(?:vpa|upi id|to vpa)\s*[:\s]\s*([a-zA-Z0-9._\-]{2,}@[a-zA-Z]{2,})""", RegexOption.IGNORE_CASE)
    private val BARE_VPA = Regex("""\b([a-zA-Z0-9._\-]{3,}@(?:ok[a-z]+|[a-z]{2,15}))\b""")

    private val MERCHANT_AT = Regex(
        """\b(?:at|to|towards|in favour of|favouring|for)\s+([A-Za-z][A-Za-z0-9&.'\- ]{2,38}?)(?=\s+(?:on|via|ref|upi|dated|thru|through|a/c|acct|info|txn|bal|avl)\b|[.,;:!\n]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val MERCHANT_INFO = Regex(
        """\b(?:info|remarks?|narration|desc|towards)\s*[:\-]\s*([^.;\n]{3,40})""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_HINT = Regex(
        """\b(?:a\/c|ac|acct|account)\s*(?:no\.?)?\s*[:\s]?\s*[xX*]{0,8}(\d{3,6})""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_HINT = Regex(
        """\bcard\s*(?:no\.?|ending|ending with|xx)?\s*[:\s]?\s*[xX*]{0,8}(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    private val REF_NO = Regex(
        """\b(?:ref(?:erence)?(?:\s*(?:no|num|id|#))?|txn(?:\s*(?:no|id|#))?|utr|rrn|transaction id)\s*[:\s#]\s*([A-Za-z0-9]{6,25})""",
        RegexOption.IGNORE_CASE
    )

    /** Marketing senders are 10-digit numbers; bank alerts use alphanumeric ids like AD-HDFCBK. */
    private val BANK_SENDER = Regex("""[A-Za-z]{2,}""")

    // ------------------------------------------------------------------ parse

    /**
     * Returns a [PendingSms] when [body] looks like a completed money movement,
     * otherwise null.
     */
    fun parse(sender: String, body: String, receivedAt: Long): PendingSms? =
        parseInternal(sender, body, receivedAt, requireBankSender = true)

    /**
     * Parses a message the user handed over deliberately — pasted in, or shared
     * from their messaging app. There is no sender to vet in that case, and the
     * user has already vouched for the message, so the sender heuristic is
     * skipped. Every other guard (OTPs, reminders, failures, promos) still runs.
     */
    fun parseShared(body: String, receivedAt: Long, sender: String = "Shared"): PendingSms? =
        parseInternal(sender, body, receivedAt, requireBankSender = false)

    private fun parseInternal(
        sender: String,
        body: String,
        receivedAt: Long,
        requireBankSender: Boolean
    ): PendingSms? {
        if (body.isBlank()) return null
        val lower = body.lowercase()

        // A purely numeric sender is almost always person-to-person chatter.
        if (requireBankSender && !BANK_SENDER.containsMatchIn(sender)) return null

        if (BLOCKERS.any { lower.contains(it) }) return null

        val debitAt = DEBIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val type = when {
            debitAt != null && creditAt != null -> if (debitAt <= creditAt) TxType.EXPENSE else TxType.INCOME
            debitAt != null -> TxType.EXPENSE
            creditAt != null -> TxType.INCOME
            else -> return null
        }

        val amount = extractAmount(body) ?: return null
        if (amount <= 0.0 || amount > 100_000_000.0) return null

        val merchant = extractMerchant(body)
        val accountHint = extractAccountHint(body)
        val hash = buildHash(sender, body, amount, receivedAt)

        return PendingSms(
            sender = sender.trim(),
            body = body.trim(),
            receivedAt = receivedAt,
            amount = amount,
            type = type,
            merchant = merchant,
            accountHint = accountHint,
            refHash = hash
        )
    }

    /**
     * Picks the transaction amount. Banks often mention both the amount and the
     * remaining balance, so any figure that is introduced as a balance is skipped
     * and the first remaining currency figure wins.
     */
    fun extractAmount(body: String): Double? {
        val balanceZones = Regex(
            """(?:avl(?:\.|able)?\s*(?:bal|balance)|bal(?:ance)?|available balance|limit)\s*[:\s]?\s*(?:is\s*)?(?:rs\.?|inr|₹)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?""",
            RegexOption.IGNORE_CASE
        ).findAll(body).map { it.range }.toList()

        fun inBalanceZone(r: IntRange) = balanceZones.any { z -> r.first >= z.first && r.first <= z.last }

        AMOUNT_AFTER.findAll(body).forEach { m ->
            if (!inBalanceZone(m.range)) toAmount(m.groupValues[1])?.let { return it }
        }
        AMOUNT_BEFORE.findAll(body).forEach { m ->
            if (!inBalanceZone(m.range)) toAmount(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun toAmount(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    fun extractMerchant(body: String): String {
        VPA.find(body)?.let { return cleanMerchant(it.groupValues[1]) }
        MERCHANT_INFO.find(body)?.let {
            val v = cleanMerchant(it.groupValues[1])
            if (v.isNotEmpty()) return v
        }
        MERCHANT_AT.find(body)?.let {
            val v = cleanMerchant(it.groupValues[1])
            if (v.isNotEmpty() && !v.equals("your", true) && !v.equals("a", true)) return v
        }
        BARE_VPA.find(body)?.let { return cleanMerchant(it.groupValues[1]) }
        return ""
    }

    private fun cleanMerchant(raw: String): String {
        var v = raw.trim().trim('-', '.', ',', ':', ';', '*', '/')
        v = v.replace(Regex("""\s{2,}"""), " ")
        // Strip trailing transaction noise the regex may have swept in.
        v = v.replace(
            Regex("""\s+(?:on|via|ref|upi|txn|dated|thru|through)\b.*$""", RegexOption.IGNORE_CASE), ""
        ).trim()
        if (v.length > 40) v = v.take(40).trim()
        return if (v.length < 2) "" else v
    }

    fun extractAccountHint(body: String): String {
        CARD_HINT.find(body)?.let { return "Card ****" + it.groupValues[1] }
        ACCOUNT_HINT.find(body)?.let { return "A/c ****" + it.groupValues[1] }
        return ""
    }

    /**
     * Stable identity for a message so the same alert is never imported twice.
     * Uses the bank reference number when present; otherwise the sender, amount
     * and a minute-rounded timestamp.
     */
    private fun buildHash(sender: String, body: String, amount: Double, receivedAt: Long): String {
        REF_NO.find(body)?.let { return "ref:" + it.groupValues[1].uppercase() }
        val normalised = body.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        val minute = receivedAt / 60_000L
        return "h:" + sender.lowercase().trim() + ":" + amount + ":" + minute + ":" +
            normalised.take(48).hashCode()
    }

    // ------------------------------------------------------ auto categorising

    /**
     * Maps a merchant / message to one of the seeded category names.
     * Returns null when nothing matches, leaving the transaction uncategorised.
     */
    fun guessCategory(merchant: String, body: String, type: TxType): String? {
        val hay = (merchant + " " + body).lowercase()
        val table = if (type == TxType.INCOME) INCOME_RULES else EXPENSE_RULES
        for ((category, keys) in table) {
            if (keys.any { hay.contains(it) }) return category
        }
        return null
    }

    private val EXPENSE_RULES: List<Pair<String, List<String>>> = listOf(
        "Food & Dining" to listOf(
            "swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "burger",
            "starbucks", "cafe", "coffee", "restaurant", "biryani", "eatfit",
            "faasos", "behrouz", "dunkin", "subway", "barbeque", "hotel "
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "dmart", "d-mart", "grofers", "instamart",
            "jiomart", "more retail", "reliance fresh", "supermarket", "kirana",
            "milkbasket", "country delight", "licious", "freshtohome"
        ),
        "Fuel & Transport" to listOf(
            "indian oil", "iocl", "bharat petroleum", "bpcl", "hpcl", "hindustan petroleum",
            "petrol", "diesel", "fuel", "shell", "nayara",
            "uber", "ola ", "olacabs", "rapido", "namma yatri", "irctc", "redbus",
            "metro rail", "bmtc", "fastag", "parking", "toll"
        ),
        "Shopping - Personal" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "tatacliq",
            "snapdeal", "shoppers stop", "lifestyle", "westside", "decathlon",
            "croma", "reliance digital", "vijay sales"
        ),
        "Shopping - Home" to listOf(
            "ikea", "pepperfry", "urban ladder", "home centre", "homecentre",
            "wakefit", "sleepwell", "furniture", "hardware", "paints", "asian paint"
        ),
        "Recharge & Subscriptions" to listOf(
            "netflix", "spotify", "hotstar", "prime video", "sonyliv", "zee5",
            "youtube premium", "google one", "icloud", "apple.com/bill",
            "recharge", "airtel", "jio", "vodafone", "vi ", "bsnl", "dth", "tata play"
        ),
        "Bills & Utilities" to listOf(
            "electricity", "bescom", "mseb", "tneb", "kseb", "adani electricity",
            "torrent power", "gas bill", "indane", "hp gas", "water bill",
            "broadband", "act fibernet", "hathway", "municipal", "bbmp", "property tax"
        ),
        "Health & Medical" to listOf(
            "apollo", "pharmeasy", "netmeds", "1mg", "tata 1mg", "medplus",
            "hospital", "clinic", "diagnostic", "lab ", "practo", "cult.fit", "cultfit",
            "pharmacy", "medical"
        ),
        "Entertainment" to listOf(
            "bookmyshow", "pvr", "inox", "cinepolis", "cinema", "multiplex",
            "gaming", "steam", "playstation", "xbox"
        ),
        "Travel" to listOf(
            "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip",
            "indigo", "air india", "vistara", "spicejet", "akasa",
            "oyo", "airbnb", "booking.com", "agoda", "trivago"
        ),
        "Education" to listOf(
            "udemy", "coursera", "byju", "unacademy", "vedantu", "school fee",
            "college", "tuition", "university", "exam fee"
        ),
        "EMI & Loan" to listOf("emi", "loan", "instalment", "installment", "bajaj finance", "hdb financial"),
        "Insurance" to listOf("insurance", "policy premium", "lic ", "premium paid", "hdfc life", "icici pru"),
        "Investment" to listOf(
            "zerodha", "groww", "upstox", "angel one", "mutual fund", "sip ",
            "nps ", "ppf", "coin.zerodha", "smallcase", "kuvera", "indmoney"
        ),
        "Personal Care" to listOf("salon", "spa ", "barber", "urban company", "urbanclap", "grooming"),
        "Clothing" to listOf("zara", "h&m", "uniqlo", "levis", "levi's", "pantaloons", "max fashion", "fabindia"),
        "Rent" to listOf("rent", "nobroker", "landlord", "maintenance charges", "society dues"),
        "Pets" to listOf("petsmart", "heads up for tails", "supertails", "vet ", "pet food"),
        "Gifts & Donations" to listOf("donation", "temple", "trust", "ngo", "charity", "gift card")
    )

    private val INCOME_RULES: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "sal cr", "payroll", "wages", "stipend"),
        "Freelance / Business" to listOf(
            "freelance", "consulting", "consultancy", "invoice", "contract payment",
            "professional fees", "honorarium", "project payment", "client payment"
        ),
        "Refund / Cashback" to listOf("refund", "cashback", "reversal", "reimburse", "returned"),
        "Interest" to listOf("interest", "int.cr", "int cr", "fd interest", "savings interest"),
        "Investment Returns" to listOf("dividend", "redemption", "maturity", "payout", "mutual fund"),
        "Rental Income" to listOf("rent received", "rent cr", "tenant")
    )
}
