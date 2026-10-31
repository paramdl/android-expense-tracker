package com.expensetracker.data.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.expensetracker.data.DateRanges
import com.expensetracker.data.PendingSms
import com.expensetracker.data.Repo
import com.expensetracker.data.Settings
import com.expensetracker.data.Transaction
import com.expensetracker.data.TxSource
import com.expensetracker.data.TxType

/**
 * Turns raw SMS into either a pending review item or, when the user has opted
 * into auto-record, straight into a transaction.
 */
object SmsIngest {

    data class ScanResult(val scanned: Int, val matched: Int, val added: Int)

    /**
     * Handles one message. Returns true when something new was stored.
     */
    fun ingest(repo: Repo, settings: Settings, sender: String, body: String, receivedAt: Long): Boolean {
        val parsed = SmsParser.parse(sender, body, receivedAt) ?: return false
        if (repo.smsHashExists(parsed.refHash)) return false

        return if (settings.smsAutoRecord) {
            val stored = repo.addPendingSms(parsed.copy(status = "ACCEPTED"))
            if (stored) commit(repo, parsed)
            stored
        } else {
            repo.addPendingSms(parsed)
        }
    }

    /** Converts an approved [PendingSms] into a real transaction. */
    fun commit(repo: Repo, p: PendingSms, categoryIdOverride: Long? = null): Long {
        val categoryId = categoryIdOverride ?: resolveCategory(repo, p)
        val accountId = resolveAccount(repo, p)
        val tx = Transaction(
            type = p.type,
            amount = p.amount,
            date = p.receivedAt,
            categoryId = categoryId,
            personId = null,
            accountId = accountId,
            merchant = p.merchant,
            note = buildNote(p),
            source = TxSource.SMS
        )
        return repo.upsertTx(tx)
    }

    private fun buildNote(p: PendingSms): String {
        val bits = ArrayList<String>()
        if (p.accountHint.isNotEmpty()) bits.add(p.accountHint)
        bits.add("via SMS from " + p.sender)
        return bits.joinToString(" · ")
    }

    private fun resolveCategory(repo: Repo, p: PendingSms): Long? {
        val guess = SmsParser.guessCategory(p.merchant, p.body, p.type) ?: return null
        return repo.categories().firstOrNull { it.type == p.type && it.name.equals(guess, true) }?.id
    }

    /**
     * Matches the account hint against known account names, else falls back to
     * a sensible default (card alerts to a card account, everything else to UPI).
     */
    private fun resolveAccount(repo: Repo, p: PendingSms): Long? {
        val accounts = repo.accounts()
        if (accounts.isEmpty()) return null
        val hint = p.accountHint.lowercase()
        val bodyLower = p.body.lowercase()

        accounts.firstOrNull { a ->
            val n = a.name.lowercase()
            n.length > 2 && (hint.contains(n) || bodyLower.contains(n))
        }?.let { return it.id }

        val wanted = when {
            hint.startsWith("card") && bodyLower.contains("credit") -> "credit card"
            hint.startsWith("card") -> "debit card"
            bodyLower.contains("upi") -> "upi / wallet"
            else -> "bank account"
        }
        return accounts.firstOrNull { it.name.equals(wanted, true) }?.id ?: accounts.first().id
    }

    /**
     * Reads the device SMS inbox and ingests everything newer than [since].
     * Caller must hold READ_SMS.
     */
    fun scanInbox(ctx: Context, repo: Repo, settings: Settings, since: Long): ScanResult {
        var scanned = 0
        var matched = 0
        var added = 0
        var newest = since

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val sel = Telephony.Sms.DATE + ">?"
        val args = arrayOf(since.toString())

        ctx.contentResolver.query(uri, cols, sel, args, Telephony.Sms.DATE + " DESC")?.use { c ->
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                val date = c.getLong(2)
                if (date > newest) newest = date

                val parsed = SmsParser.parse(sender, body, date)
                if (parsed != null) {
                    matched++
                    if (!repo.smsHashExists(parsed.refHash)) {
                        if (settings.smsAutoRecord) {
                            if (repo.addPendingSms(parsed.copy(status = "ACCEPTED"))) {
                                commit(repo, parsed); added++
                            }
                        } else if (repo.addPendingSms(parsed)) {
                            added++
                        }
                    }
                }
            }
        }
        settings.lastSmsScanAt = newest
        return ScanResult(scanned, matched, added)
    }

    /** Convenience for the "paste a message" tester in Settings. */
    fun describe(p: PendingSms?, currency: String): String {
        if (p == null) return "No transaction found in that message."
        val dir = if (p.type == TxType.INCOME) "Income" else "Expense"
        val parts = ArrayList<String>()
        parts.add(dir + " " + currency + String.format("%,.2f", p.amount))
        if (p.merchant.isNotEmpty()) parts.add("Merchant: " + p.merchant)
        if (p.accountHint.isNotEmpty()) parts.add(p.accountHint)
        parts.add(DateRanges.iso(p.receivedAt))
        return parts.joinToString("\n")
    }
}
