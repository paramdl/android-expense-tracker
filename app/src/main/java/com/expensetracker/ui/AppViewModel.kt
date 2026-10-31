package com.expensetracker.ui

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.ExpenseApp
import com.expensetracker.data.Account
import com.expensetracker.data.Backup
import com.expensetracker.data.Bucket
import com.expensetracker.data.CategoryBudget
import com.expensetracker.data.Category
import com.expensetracker.data.DateRanges
import com.expensetracker.data.PendingSms
import com.expensetracker.data.PayKind
import com.expensetracker.data.PayTag
import com.expensetracker.data.Perf
import com.expensetracker.data.imports.LedgerImport
import com.expensetracker.data.imports.Sheet
import com.expensetracker.data.imports.Tabular
import com.expensetracker.data.imports.UnreadableSheet
import com.expensetracker.data.Person
import com.expensetracker.data.Recurrence
import com.expensetracker.data.RecurringRule
import com.expensetracker.data.Totals
import com.expensetracker.data.Transaction
import com.expensetracker.data.TxType
import com.expensetracker.data.TxView
import com.expensetracker.data.sms.SmsIngest
import com.expensetracker.data.sms.SmsParser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RangeMode { WEEK, MONTH, YEAR, ALL, CUSTOM }

@Immutable
data class Period(
    val mode: RangeMode = RangeMode.MONTH,
    val anchor: Long = System.currentTimeMillis(),
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
    val label: String = ""
)

@Immutable
data class Lookups(
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val modes: List<PayTag> = emptyList(),
    val methods: List<PayTag> = emptyList(),
    val banks: List<PayTag> = emptyList(),
    // Split once when the lookups load. These used to be computed properties, so
    // the entry screen re-filtered the whole category list on every recomposition.
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList()
)

private fun buildLookups(
    cats: List<Category>,
    people: List<Person>,
    accounts: List<Account>,
    modes: List<PayTag> = emptyList(),
    methods: List<PayTag> = emptyList(),
    banks: List<PayTag> = emptyList()
) =
    Lookups(
        categories = cats,
        people = people,
        accounts = accounts,
        modes = modes,
        methods = methods,
        banks = banks,
        expenseCategories = cats.filter { it.type == TxType.EXPENSE },
        incomeCategories = cats.filter { it.type == TxType.INCOME }
    )

/** Figures that depend only on the data, never on the selected period. */
@Immutable
private data class GlobalData(
    val accountBalances: List<Pair<Account, Double>> = emptyList(),
    val allTimeTotals: Totals = Totals(0.0, 0.0),
    val txCount: Int = 0
)

@Immutable
data class BackupState(
    val folderUri: String? = null,
    val lastBackupAt: Long = 0L,
    val lastSmsScanAt: Long = 0L
)

private data class RefreshedGlobals(
    val lookups: Lookups,
    val globals: GlobalData,
    val pending: List<PendingSms>,
    val rules: List<RecurringRule>,
    val upcoming: List<Pair<RecurringRule, Long>>
)

private data class LedgerKey(
    val version: Long,
    val period: Period,
    val filter: TxFilter,
    val active: Boolean
)

@Immutable
data class DashData(
    val totals: Totals = Totals(0.0, 0.0),
    val expenseByCategory: List<Bucket> = emptyList(),
    val incomeByCategory: List<Bucket> = emptyList(),
    val byPerson: List<Bucket> = emptyList(),
    val expenseByAccount: List<Bucket> = emptyList(),
    val topMerchants: List<Bucket> = emptyList(),
    val trend: List<Triple<String, Double, Double>> = emptyList(),
    val daily: List<Pair<Long, Double>> = emptyList(),
    val recent: List<TxView> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val accountBalances: List<Pair<Account, Double>> = emptyList(),
    val allTimeTotals: Totals = Totals(0.0, 0.0),
    val txCount: Int = 0
)

@Immutable
data class TxFilter(
    val type: TxType? = null,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val query: String = "",
    val usePeriod: Boolean = true
) {
    val activeCount: Int
        get() = listOfNotNull(type, categoryId, personId, accountId).size + (if (query.isBlank()) 0 else 1)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as ExpenseApp
    private val repo = appCtx.repo
    val settings = appCtx.settings

    private val _period = MutableStateFlow(computePeriod(RangeMode.MONTH, System.currentTimeMillis()))
    val period: StateFlow<Period> = _period.asStateFlow()

    private val _dash = MutableStateFlow(DashData())
    val dash: StateFlow<DashData> = _dash.asStateFlow()

    private val _lookups = MutableStateFlow(Lookups())
    val lookups: StateFlow<Lookups> = _lookups.asStateFlow()

    private val _filter = MutableStateFlow(TxFilter())
    val filter: StateFlow<TxFilter> = _filter.asStateFlow()

    private val _txList = MutableStateFlow<List<TxView>>(emptyList())
    val txList: StateFlow<List<TxView>> = _txList.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingSms>>(emptyList())
    val pending: StateFlow<List<PendingSms>> = _pending.asStateFlow()

    private val _currency = MutableStateFlow(settings.currencySymbol)
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _theme = MutableStateFlow(settings.themeMode)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _budget = MutableStateFlow(settings.monthlyBudget)
    val budget: StateFlow<Double> = _budget.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** True while a just-deleted transaction can still be restored. */
    private val _undoable = MutableStateFlow(false)
    val undoable: StateFlow<Boolean> = _undoable.asStateFlow()

    /** True only while the Ledger tab is on screen. */
    private val _ledgerActive = MutableStateFlow(false)

    /** Set when the ledger query had more rows than [LEDGER_PAGE]. */
    private val _ledgerTruncated = MutableStateFlow(false)
    val ledgerTruncated: StateFlow<Boolean> = _ledgerTruncated.asStateFlow()

    private val _rules = MutableStateFlow<List<RecurringRule>>(emptyList())
    val rules: StateFlow<List<RecurringRule>> = _rules.asStateFlow()

    private val _upcoming = MutableStateFlow<List<Pair<RecurringRule, Long>>>(emptyList())
    val upcoming: StateFlow<List<Pair<RecurringRule, Long>>> = _upcoming.asStateFlow()

    /** A launcher shortcut or widget tap, waiting to be routed to a screen. */
    enum class Quick { ADD, IMPORT }

    private val _quickAction = MutableStateFlow<Quick?>(null)
    val quickAction: StateFlow<Quick?> = _quickAction.asStateFlow()

    fun setQuickAction(q: Quick?) {
        _quickAction.value = q
    }

    /** Text shared into the app from another app, awaiting import. */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    /** Backup folder + last-run state, so Settings can redraw without a DB pass. */
    private val _backupState = MutableStateFlow(readBackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private fun readBackupState() = BackupState(
        folderUri = settings.backupFolderUri,
        lastBackupAt = settings.lastBackupAt,
        lastSmsScanAt = settings.lastSmsScanAt
    )

    @Volatile
    private var globals = GlobalData()

    init {
        // Three independent pipelines, so a change only redoes the work it affects.
        // collectLatest cancels a pass that is still running, which collapses a
        // burst of writes into a single refresh instead of one refresh per row.

        // 1. Lookup tables and whole-ledger figures — data changes only.
        viewModelScope.launch {
            repo.version.collectLatest { refreshGlobals() }
        }

        // 2. Dashboard aggregates — data or period.
        viewModelScope.launch {
            combine(repo.version, _period) { _, p -> p }
                .collectLatest { p -> refreshDash(p) }
        }

        // 3. Ledger rows — data, period or filter, and only while that tab is up.
        // Debounced so typing in the search box runs one query when the user
        // pauses instead of one per keystroke.
        viewModelScope.launch {
            combine(repo.version, _period, _filter, _ledgerActive) { v, p, f, active ->
                LedgerKey(v, p, f, active)
            }
                .distinctUntilChanged()
                .debounce { key -> if (key.filter.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .collectLatest { key ->
                    if (key.active) refreshLedger(key.period, key.filter) else _txList.value = emptyList()
                }
        }
    }

    fun setLedgerActive(active: Boolean) {
        _ledgerActive.value = active
    }

    fun toast(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------ period ctrl

    private fun computePeriod(mode: RangeMode, anchor: Long, from: Long = 0, to: Long = 0): Period =
        when (mode) {
            RangeMode.MONTH -> {
                val b = DateRanges.monthBounds(anchor)
                Period(mode, anchor, b.first, b.second, DateRanges.monthLabel(anchor))
            }
            RangeMode.WEEK -> {
                val b = DateRanges.weekBounds(anchor)
                Period(
                    mode, anchor, b.first, b.second,
                    DateRanges.dayShort(b.first) + " – " + DateRanges.dayShort(b.second)
                )
            }
            RangeMode.YEAR -> {
                val b = DateRanges.yearBounds(anchor)
                Period(mode, anchor, b.first, b.second, DateRanges.monthLabel(anchor).takeLast(4))
            }
            RangeMode.ALL -> Period(mode, anchor, 0L, Long.MAX_VALUE, "All time")
            RangeMode.CUSTOM -> Period(
                mode, anchor, from, to,
                DateRanges.dayShort(from) + " – " + DateRanges.dayShort(to)
            )
        }

    fun setRangeMode(mode: RangeMode) {
        _period.value = computePeriod(mode, System.currentTimeMillis())
    }

    fun setCustomRange(from: Long, to: Long) {
        _period.value = computePeriod(
            RangeMode.CUSTOM, from, DateRanges.startOfDay(from), DateRanges.endOfDay(to)
        )
    }

    /** Steps the anchor one period backwards or forwards. */
    fun shiftPeriod(delta: Int) {
        val p = _period.value
        val newAnchor = when (p.mode) {
            RangeMode.MONTH -> DateRanges.addMonths(p.anchor, delta)
            RangeMode.WEEK -> p.anchor + delta * 7L * 86_400_000L
            RangeMode.YEAR -> DateRanges.addMonths(p.anchor, delta * 12)
            else -> return
        }
        _period.value = computePeriod(p.mode, newAnchor)
    }

    fun canShift(): Boolean = _period.value.mode != RangeMode.ALL && _period.value.mode != RangeMode.CUSTOM

    // ---------------------------------------------------------------- refresh

    /** Lookup tables and whole-ledger figures. Independent of the period. */
    private suspend fun refreshGlobals() {
        val started = System.nanoTime()
        val result = withContext(Dispatchers.IO) { Perf.trace("io.globals") {
            val lk = buildLookups(
                repo.categories(), repo.people(), repo.accounts(),
                repo.payTags(PayKind.MODE), repo.payTags(PayKind.METHOD), repo.payTags(PayKind.BANK)
            )
            val g = GlobalData(
                accountBalances = repo.accountBalances(),
                allTimeTotals = repo.totals(0L, Long.MAX_VALUE),
                txCount = repo.txCount()
            )
            RefreshedGlobals(lk, g, repo.pendingSms(), repo.recurringRules(), repo.upcomingRecurring())
        } }
        _lookups.value = result.lookups
        globals = result.globals
        _pending.value = result.pending
        _rules.value = result.rules
        _upcoming.value = result.upcoming
        // Keep the home screen widget in step with the ledger.
        com.expensetracker.widget.SummaryWidget.refresh(getApplication())
        // Fold the fresh whole-ledger figures into whatever the dashboard shows.
        _dash.value = _dash.value.copy(
            accountBalances = result.globals.accountBalances,
            allTimeTotals = result.globals.allTimeTotals,
            txCount = result.globals.txCount
        )
        Perf.report("refresh.globals", (System.nanoTime() - started) / 1_000_000.0)
    }

    /** Period-scoped aggregates behind the dashboard and reports. */
    private suspend fun refreshDash(p: Period) {
        val started = System.nanoTime()
        val data = withContext(Dispatchers.IO) { Perf.trace("io.dash") {
            val from = p.from
            val to = p.to
            val now = System.currentTimeMillis()
            val dailyTo = minOf(to, now)
            DashData(
                totals = repo.totals(from, to),
                expenseByCategory = repo.byCategory(from, to, TxType.EXPENSE),
                incomeByCategory = repo.byCategory(from, to, TxType.INCOME),
                byPerson = repo.byPerson(from, to, null),
                expenseByAccount = repo.byAccount(from, to, TxType.EXPENSE),
                topMerchants = repo.topMerchants(from, to),
                trend = repo.monthlyTrend(if (p.mode == RangeMode.ALL) now else p.anchor, 6),
                daily = repo.dailyExpense(
                    maxOf(from, DateRanges.startOfDay(dailyTo) - 29L * 86_400_000L),
                    dailyTo
                ),
                recent = repo.transactions(from = from, to = to, limit = 6),
                budgets = repo.categoryBudgets(from, to)
            )
        } }
        val g = globals
        _dash.value = data.copy(
            accountBalances = g.accountBalances,
            allTimeTotals = g.allTimeTotals,
            txCount = g.txCount
        )
        Perf.report("refresh.dash", (System.nanoTime() - started) / 1_000_000.0)
    }

    /** The Ledger tab's rows. Skipped entirely while that tab is off screen. */
    private suspend fun refreshLedger(p: Period, f: TxFilter) {
        val started = System.nanoTime()
        val list = withContext(Dispatchers.IO) {
            repo.transactions(
                from = if (f.usePeriod) p.from else null,
                to = if (f.usePeriod) p.to else null,
                type = f.type,
                categoryId = f.categoryId,
                personId = f.personId,
                accountId = f.accountId,
                query = f.query,
                // One extra row tells us whether anything was left off.
                limit = LEDGER_PAGE + 1
            )
        }
        _ledgerTruncated.value = list.size > LEDGER_PAGE
        _txList.value = if (list.size > LEDGER_PAGE) list.subList(0, LEDGER_PAGE) else list
        Perf.report("refresh.ledger", (System.nanoTime() - started) / 1_000_000.0)
    }

    /** Re-runs every pipeline; used after a change made outside the ViewModel. */
    fun reload() = repo.notifyChanged()

    // ----------------------------------------------------------------- filter

    fun setFilter(f: TxFilter) {
        _filter.value = f
    }

    fun clearFilter() = setFilter(TxFilter())

    // ----------------------------------------------------------- transactions

    fun saveTx(tx: Transaction, newPersonName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.batch {
                val personId = when {
                    !newPersonName.isNullOrBlank() -> repo.personByNameOrCreate(newPersonName)
                    else -> tx.personId
                }
                repo.upsertTx(tx.copy(personId = personId?.takeIf { it != 0L }))
            }
            _message.value = if (tx.id == 0L) "Transaction added" else "Transaction updated"
        }
    }

    /** Last deleted transaction, held so the snackbar can put it back. */
    private var lastDeleted: Transaction? = null

    fun deleteTx(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            lastDeleted = repo.txById(id)
            repo.deleteTx(id)
            _undoable.value = lastDeleted != null
            _message.value = "Transaction deleted"
        }
    }

    fun undoDelete() {
        val t = lastDeleted ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.restoreTx(t)
            lastDeleted = null
            _undoable.value = false
            _message.value = "Restored"
        }
    }

    fun merchantSuggestions(prefix: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.merchantSuggestions(prefix) }
            onResult(list)
        }
    }

    fun txById(id: Long, onResult: (Transaction?) -> Unit) {
        viewModelScope.launch {
            val t = withContext(Dispatchers.IO) { repo.txById(id) }
            onResult(t)
        }
    }

    // --------------------------------------------------------------- lookups

    fun saveCategory(c: Category) = viewModelScope.launch(Dispatchers.IO) { repo.upsertCategory(c) }
    fun deleteCategory(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteCategory(id) }
    fun savePerson(p: Person) = viewModelScope.launch(Dispatchers.IO) { repo.upsertPerson(p) }
    fun deletePerson(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deletePerson(id) }
    fun saveAccount(a: Account) = viewModelScope.launch(Dispatchers.IO) { repo.upsertAccount(a) }
    fun deleteAccount(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteAccount(id) }

    // -------------------------------------------------------------- settings

    fun setCurrency(sym: String) {
        settings.currencySymbol = sym
        _currency.value = sym
    }

    fun setTheme(mode: String) {
        settings.themeMode = mode
        _theme.value = mode
    }

    fun setBudget(v: Double) {
        settings.monthlyBudget = v
        _budget.value = v
    }

    // ------------------------------------------------------------------- SMS

    fun setSmsEnabled(on: Boolean) {
        settings.smsEnabled = on
    }

    fun setSmsAutoRecord(on: Boolean) {
        settings.smsAutoRecord = on
    }

    /** Full inbox sweep. [full] ignores the last-scan watermark. */
    fun scanInbox(full: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val res = withContext(Dispatchers.IO) {
                val since = if (full) 0L else settings.lastSmsScanAt
                // One refresh at the end, not one per matched message.
                repo.batch { SmsIngest.scanInbox(getApplication(), repo, settings, since) }
            }
            _busy.value = false
            _message.value = if (res.added == 0) {
                "Scanned " + res.scanned + " messages, nothing new to add"
            } else if (settings.smsAutoRecord) {
                "Recorded " + res.added + " transactions from SMS"
            } else {
                res.added.toString() + " messages ready to review"
            }
            _backupState.value = readBackupState()
        }
    }

    fun acceptSms(p: PendingSms, categoryId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.batch {
                SmsIngest.commit(repo, p, categoryId)
                repo.setSmsStatus(p.id, "ACCEPTED")
            }
            _message.value = "Added to your ledger"
        }
    }

    fun acceptAllSms() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repo.pendingSms()
            repo.inTransaction {
                list.forEach {
                    SmsIngest.commit(repo, it)
                    repo.setSmsStatus(it.id, "ACCEPTED")
                }
            }
            _message.value = "Added " + list.size + " transactions"
        }
    }

    fun ignoreSms(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.setSmsStatus(id, "IGNORED")
    }

    fun ignoreAllSms() = viewModelScope.launch(Dispatchers.IO) {
        repo.inTransaction {
            repo.pendingSms().forEach { repo.setSmsStatus(it.id, "IGNORED") }
        }
        _message.value = "Cleared the review queue"
    }

    // ---------------------------------------------------------------- import

    fun setSharedText(text: String?) {
        _sharedText.value = text
    }

    /**
     * Parses a message the user pasted or shared in and, if it holds a
     * transaction, records it. Needs no SMS permission — the text was handed
     * over by the user.
     */
    fun importMessage(text: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val parsed = SmsParser.parseShared(text.trim(), System.currentTimeMillis())
                when {
                    parsed == null -> false to
                        "No transaction found in that message. Check it names an amount and " +
                        "says debited, credited, spent or paid."
                    repo.smsHashExists(parsed.refHash) -> false to
                        "That message has already been imported."
                    else -> {
                        repo.batch {
                            repo.addPendingSms(parsed.copy(status = "ACCEPTED"))
                            SmsIngest.commit(repo, parsed)
                        }
                        true to (
                            (if (parsed.type == TxType.INCOME) "Income " else "Expense ") +
                                _currency.value + String.format("%,.2f", parsed.amount) +
                                (if (parsed.merchant.isNotEmpty()) " · " + parsed.merchant else "") +
                                " added"
                            )
                    }
                }
            }
            onResult(outcome.first, outcome.second)
        }
    }

    /** Preview only — parses without writing, for the live preview card. */
    fun previewMessage(text: String): PendingSms? =
        if (text.isBlank()) null
        else SmsParser.parseShared(text.trim(), System.currentTimeMillis())

    fun addPayTag(kind: PayKind, name: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.payTagByNameOrCreate(kind, name) }
    }

    // -------------------------------------------------------- file import

    private var pendingSheet: Sheet? = null

    private val _importPreview = MutableStateFlow<LedgerImport.Preview?>(null)
    val importPreview: StateFlow<LedgerImport.Preview?> = _importPreview.asStateFlow()

    /** Reads the picked spreadsheet and works out what its columns mean. */
    fun openImportFile(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _busy.value = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<android.app.Application>()
                    // Everything is staged locally: ZipFile needs random access,
                    // and sniffing the content beats trusting the file name,
                    // which is routinely wrong on exports from other apps.
                    val tmp = File(ctx.cacheDir, "import-staging.bin")
                    val opened = ctx.contentResolver.openInputStream(uri)
                        ?: throw UnreadableSheet(
                            "That file could not be opened. If it lives on Google Drive, " +
                                "download it to the phone first, then pick it here."
                        )
                    opened.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    try {
                        Tabular.parse(tmp)
                    } finally {
                        tmp.delete()
                    }
                }
            }
            _busy.value = false
            outcome.onSuccess { sheet ->
                pendingSheet = sheet
                _importPreview.value = LedgerImport.detect(sheet)
            }.onFailure {
                pendingSheet = null
                _importPreview.value = null
                // Our own messages are already written for the user; anything
                // else is a raw exception and needs framing.
                _message.value =
                    if (it is UnreadableSheet) {
                        it.message ?: "That file could not be read."
                    } else {
                        "Could not read " + displayName.ifBlank { "that file" } + ": " +
                            (it.message ?: "unknown error")
                    }
            }
        }
    }

    fun clearImportPreview() {
        pendingSheet = null
        _importPreview.value = null
    }

    fun runImport() {
        val sheet = pendingSheet ?: return
        val mapping = _importPreview.value?.mapping ?: return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) { LedgerImport.run(repo, sheet, mapping) }
            _busy.value = false
            clearImportPreview()
            _message.value = when {
                result.error != null -> result.error
                result.imported == 0 && result.duplicates > 0 ->
                    "Already imported — " + result.duplicates + " duplicate rows skipped"
                else -> buildString {
                    append("Imported ").append(result.imported)
                    if (result.duplicates > 0) append(", ").append(result.duplicates).append(" duplicates")
                    if (result.skipped > 0) append(", ").append(result.skipped).append(" unreadable")
                }
            }
        }
    }

    // ------------------------------------------------------------- recurring

    fun saveRule(r: RecurringRule) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.batch {
                val id = repo.upsertRecurring(r)
                // A brand new rule whose start date is already in the past should
                // fill in its history straight away rather than waiting a cycle.
                if (r.id == 0L && id != -1L) repo.postDueRecurring()
            }
            _message.value = if (r.id == 0L) "Recurring transaction added" else "Recurring transaction updated"
        }
    }

    fun setRuleActive(id: Long, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.batch {
                repo.setRecurringActive(id, active)
                if (active) repo.postDueRecurring()
            }
            _message.value = if (active) "Resumed" else "Paused"
        }
    }

    fun deleteRule(id: Long, alsoDeletePosted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteRecurring(id, alsoDeletePosted)
            _message.value = if (alsoDeletePosted) "Deleted with its transactions" else "Deleted"
        }
    }

    fun ruleById(id: Long, onResult: (RecurringRule?) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { repo.recurringRules().firstOrNull { it.id == id } }
            onResult(r)
        }
    }

    /** Posts anything that has come due. Called on every app start. */
    fun postDueRecurring() {
        viewModelScope.launch(Dispatchers.IO) {
            val n = repo.postDueRecurring()
            if (n > 0) {
                _message.value = n.toString() +
                    (if (n == 1) " recurring transaction added" else " recurring transactions added")
            }
        }
    }

    // ---------------------------------------------------------------- backup

    fun backupJson(onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { Backup.buildJson(repo, settings) }
            onReady(json, Backup.defaultFileName())
        }
    }

    fun exportCsv(onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            val p = _period.value
            val from = if (p.from == 0L) (repo.earliestTxDate() ?: 0L) else p.from
            val to = if (p.to == Long.MAX_VALUE) System.currentTimeMillis() else p.to
            val csv = withContext(Dispatchers.IO) { Backup.buildCsv(repo, from, to, _currency.value) }
            onReady(csv, Backup.csvFileName(from, to))
        }
    }

    fun writeBackupTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) {
                val json = Backup.buildJson(repo, settings)
                Backup.writeToUri(getApplication(), uri, json)
            }
            if (ok) {
                settings.lastBackupAt = System.currentTimeMillis()
                settings.lastBackupName = "Saved via file picker"
            }
            _busy.value = false
            _message.value = if (ok) "Backup saved" else "Could not write that file"
        }
    }

    fun writeCsvTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val p = _period.value
            val from = if (p.from == 0L) (repo.earliestTxDate() ?: 0L) else p.from
            val to = if (p.to == Long.MAX_VALUE) System.currentTimeMillis() else p.to
            val ok = withContext(Dispatchers.IO) {
                Backup.writeToUri(getApplication(), uri, Backup.buildCsv(repo, from, to, _currency.value))
            }
            _busy.value = false
            _message.value = if (ok) "CSV exported" else "Could not write that file"
        }
    }

    fun restoreFrom(uri: Uri, replace: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                val text = Backup.readFromUri(getApplication(), uri)
                if (text == null) null else Backup.restore(repo, text, replace)
            }
            _busy.value = false
            _message.value = when {
                result == null -> "Could not open that file"
                !result.ok -> result.error
                else -> "Restored " + result.transactions + " transactions"
            }
        }
    }

    /** Remembers a SAF folder (e.g. a Google Drive folder) for one-tap backups. */
    fun setBackupFolder(uri: Uri) {
        settings.backupFolderUri = uri.toString()
        settings.autoBackupEnabled = true
        _message.value = "Backup folder linked"
        _backupState.value = readBackupState()
    }

    fun clearBackupFolder() {
        settings.backupFolderUri = null
        settings.autoBackupEnabled = false
        _backupState.value = readBackupState()
    }

    /** Writes a timestamped backup into the linked folder. */
    fun backupToLinkedFolder(silent: Boolean = false) {
        val folder = settings.backupFolderUri ?: run {
            if (!silent) _message.value = "Link a backup folder first"
            return
        }
        viewModelScope.launch {
            if (!silent) _busy.value = true
            val name = withContext(Dispatchers.IO) {
                Backup.writeToFolder(
                    getApplication(), Uri.parse(folder),
                    Backup.defaultFileName(), Backup.buildJson(repo, settings), Backup.MIME_JSON
                )
            }
            if (name != null) {
                settings.lastBackupAt = System.currentTimeMillis()
                settings.lastBackupName = name
            }
            if (!silent) {
                _busy.value = false
                _message.value = if (name != null) "Backed up to your folder" else "Backup folder is not writable"
            }
            _backupState.value = readBackupState()
        }
    }

    /** Called on app start: backs up once a day if the user enabled it. */
    fun maybeAutoBackup() {
        if (!settings.autoBackupEnabled) return
        if (settings.backupFolderUri == null) return
        if (System.currentTimeMillis() - settings.lastBackupAt < 20L * 3600_000L) return
        backupToLinkedFolder(silent = true)
    }

    companion object {
        /**
         * Upper bound on rows handed to the ledger list. Three years of daily
         * spending is well under this; the cap only bites on "All time" for very
         * large ledgers, where the UI says so rather than silently hiding rows.
         */
        const val LEDGER_PAGE = 1000
        private const val SEARCH_DEBOUNCE_MS = 220L
    }

    fun wipeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.wipeTransactions()
            _message.value = "All transactions deleted"
        }
    }
}
