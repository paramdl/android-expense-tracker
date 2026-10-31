package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.DateRanges
import com.expensetracker.data.EndMode
import com.expensetracker.data.Frequency
import com.expensetracker.data.PayKind
import com.expensetracker.data.Recurrence
import com.expensetracker.data.RecurringRule
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.PayTagRow
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

/** Ready-made schedules, so the common cases are one tap. */
private data class Preset(val label: String, val freq: Frequency, val interval: Int)

private val PRESETS = listOf(
    Preset("Monthly", Frequency.MONTHLY, 1),
    Preset("Every 3 months", Frequency.QUARTERLY, 1),
    Preset("Every 6 months", Frequency.MONTHLY, 6),
    Preset("Yearly", Frequency.YEARLY, 1),
    Preset("Weekly", Frequency.WEEKLY, 1),
    Preset("Daily", Frequency.DAILY, 1)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(
    vm: AppViewModel,
    ruleId: Long,
    onDone: () -> Unit
) {
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(ruleId == 0L) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var personId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var modeId by remember { mutableStateOf<Long?>(null) }
    var methodId by remember { mutableStateOf<Long?>(null) }
    var bankId by remember { mutableStateOf<Long?>(null) }
    var newTagKind by remember { mutableStateOf<PayKind?>(null) }
    var newTagName by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var interval by remember { mutableStateOf(1) }
    var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var endMode by remember { mutableStateOf(EndMode.NEVER) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var totalText by remember { mutableStateOf("") }
    var generatedCount by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf(true) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (ruleId != 0L) {
            vm.ruleById(ruleId) { r ->
                if (r != null) {
                    name = r.name; type = r.type
                    amountText = trimAmount(r.amount)
                    categoryId = r.categoryId; accountId = r.accountId; personId = r.personId
                    toAccountId = r.toAccountId
                    modeId = r.modeId; methodId = r.methodId; bankId = r.bankId
                    note = r.note; frequency = r.frequency; interval = r.intervalCount
                    startDate = r.startDate; endMode = r.endMode; endDate = r.endDate
                    totalText = r.totalCount?.toString() ?: ""
                    generatedCount = r.generatedCount; active = r.active
                    createdAt = r.createdAt
                }
                loaded = true
            }
        }
    }

    val savings = type == TxType.TRANSFER
    val categories = if (type == TxType.INCOME) lookups.incomeCategories else lookups.expenseCategories
    LaunchedEffect(type) {
        if (categoryId != null && categories.none { it.id == categoryId }) categoryId = null
        // A savings run is a move between the user's own accounts, so the
        // category and person of an expense no longer apply.
        if (savings) { categoryId = null; personId = null } else toAccountId = null
    }

    val amount = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
    val total = totalText.toIntOrNull()
    val canSave = name.isNotBlank() && amount > 0.0 &&
        (endMode != EndMode.AFTER_N || (total != null && total > 0)) &&
        (endMode != EndMode.ON_DATE || endDate != null) &&
        // Savings needs somewhere to come from and somewhere to land.
        (!savings || (accountId != null && toAccountId != null && accountId != toAccountId))

    val accent = when (type) {
        TxType.INCOME -> positiveColor()
        TxType.TRANSFER -> MaterialTheme.colorScheme.primary
        else -> negativeColor()
    }

    // Live preview of what the schedule will actually do.
    val preview = remember(name, amount, frequency, interval, startDate, endMode, endDate, totalText) {
        val draft = RecurringRule(
            name = name.ifBlank { "This" }, type = type, amount = amount,
            frequency = frequency, intervalCount = interval, startDate = startDate,
            endMode = endMode, endDate = endDate, totalCount = total, generatedCount = 0
        )
        (0 until 3).mapNotNull { i ->
            if (Recurrence.isFinished(draft, i)) null else Recurrence.occurrenceAt(draft, i)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == 0L) "New recurring" else "Edit recurring") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        if (!loaded) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("💸  Expense", type == TxType.EXPENSE, { type = TxType.EXPENSE },
                        Modifier.weight(1f), negativeColor())
                    Pill("🐷  Savings", savings, { type = TxType.TRANSFER },
                        Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    Pill("💰  Income", type == TxType.INCOME, { type = TxType.INCOME },
                        Modifier.weight(1f), positiveColor())
                }
            }

            item {
                SectionCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = {
                            Text(
                                if (savings) "Chit fund, RD, Emergency fund…"
                                else "Netflix, Car EMI, Rent…"
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v ->
                            val f = v.filter { it.isDigit() || it == '.' }
                            if (f.count { it == '.' } <= 1) amountText = f
                        },
                        label = { Text("Amount each time (" + currency + ")") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle("How often")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESETS.forEach { p ->
                            Pill(
                                p.label,
                                frequency == p.freq && interval == p.interval,
                                { frequency = p.freq; interval = p.interval }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Caption("REPEAT EVERY")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(1, 2, 3, 6, 12).forEach { n ->
                            Pill(n.toString(), interval == n, { interval = n })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Frequency.entries.forEach { f ->
                            Pill(f.name.lowercase().replaceFirstChar { it.uppercase() },
                                frequency == f, { frequency = f })
                        }
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitle("Starts and ends")
                    Spacer(Modifier.height(12.dp))
                    DateRow("First payment", DateRanges.dayFullLabel(startDate)) {
                        showStartPicker = true
                    }
                    Spacer(Modifier.height(14.dp))
                    Caption("ENDS")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pill("Never", endMode == EndMode.NEVER, { endMode = EndMode.NEVER })
                        Pill("After N payments", endMode == EndMode.AFTER_N,
                            { endMode = EndMode.AFTER_N })
                        Pill("On a date", endMode == EndMode.ON_DATE, { endMode = EndMode.ON_DATE })
                    }

                    if (endMode == EndMode.AFTER_N) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { v -> totalText = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Number of payments") },
                            placeholder = { Text("36 for a 3-year EMI") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (total != null && total > 0 && amount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Caption(
                                "Total over the term: " + Fmt.money(currency, amount * total) +
                                    (if (generatedCount > 0) "  ·  " + generatedCount + " already paid" else "")
                            )
                        }
                    }

                    if (endMode == EndMode.ON_DATE) {
                        Spacer(Modifier.height(14.dp))
                        DateRow(
                            "Last payment on or before",
                            endDate?.let { DateRanges.dayFullLabel(it) } ?: "Choose a date"
                        ) { showEndPicker = true }
                    }
                }
            }

            item {
                SectionCard {
                    if (!savings) {
                        SectionTitle("Category")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { c ->
                                Pill(c.emoji + "  " + c.name, categoryId == c.id, {
                                    categoryId = if (categoryId == c.id) null else c.id
                                })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    SectionTitle(if (savings) "Money comes from" else "Account")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lookups.accounts.forEach { a ->
                            Pill(a.emoji + "  " + a.name, accountId == a.id, {
                                accountId = if (accountId == a.id) null else a.id
                                if (toAccountId == accountId) toAccountId = null
                            })
                        }
                    }

                    if (savings) {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("Money goes to")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // The same account on both sides would be a no-op.
                            lookups.accounts.filter { it.id != accountId }.forEach { a ->
                                Pill(a.emoji + "  " + a.name, toAccountId == a.id, {
                                    toAccountId = if (toAccountId == a.id) null else a.id
                                })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Caption(
                            "Each run moves " + Fmt.money(currency, amount) + " between your " +
                                "own accounts. Both balances change, and it stays out of " +
                                "spending and category totals so it never reads as an expense."
                        )
                    }

                    if (!savings && lookups.people.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Person")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lookups.people.forEach { p ->
                                Pill(p.name, personId == p.id, {
                                    personId = if (personId == p.id) null else p.id
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle("How it is paid")
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        "Optional, and copied onto every payment this rule creates. " +
                            "Tap \u201C+ Add\u201D on any row to record your own."
                    )

                    PayTagRow(
                        label = "MODE",
                        tags = lookups.modes,
                        selectedId = modeId,
                        onSelect = { modeId = if (modeId == it) null else it },
                        onAdd = { newTagKind = PayKind.MODE; newTagName = "" }
                    )
                    PayTagRow(
                        label = "METHOD",
                        tags = lookups.methods,
                        selectedId = methodId,
                        onSelect = { methodId = if (methodId == it) null else it },
                        onAdd = { newTagKind = PayKind.METHOD; newTagName = "" }
                    )
                    PayTagRow(
                        label = "BANK",
                        tags = lookups.banks,
                        selectedId = bankId,
                        onSelect = { bankId = if (bankId == it) null else it },
                        onAdd = { newTagKind = PayKind.BANK; newTagName = "" }
                    )
                }
            }

            if (preview.isNotEmpty()) {
                item {
                    SectionCard {
                        SectionTitle("Next few dates")
                        Spacer(Modifier.height(10.dp))
                        preview.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    DateRanges.dayFullLabel(t),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    Fmt.money(currency, amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accent
                                )
                            }
                        }
                        if (startDate < System.currentTimeMillis()) {
                            Spacer(Modifier.height(8.dp))
                            Caption(
                                "The start date is in the past, so any payments already due will " +
                                    "be added to your ledger straight away."
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.saveRule(
                            RecurringRule(
                                id = ruleId,
                                name = name.trim(),
                                type = type,
                                amount = amount,
                                categoryId = if (savings) null else categoryId,
                                personId = if (savings) null else personId,
                                accountId = accountId,
                                toAccountId = if (savings) toAccountId else null,
                                modeId = modeId,
                                methodId = methodId,
                                bankId = bankId,
                                merchant = name.trim(),
                                note = note.trim(),
                                frequency = frequency,
                                intervalCount = interval,
                                startDate = startDate,
                                endMode = endMode,
                                endDate = if (endMode == EndMode.ON_DATE) endDate else null,
                                totalCount = if (endMode == EndMode.AFTER_N) total else null,
                                generatedCount = generatedCount,
                                active = active,
                                createdAt = createdAt
                            )
                        )
                        onDone()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(
                        if (ruleId == 0L) "Create recurring" else "Save changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    newTagKind?.let { kind ->
        val what = when (kind) {
            PayKind.MODE -> "payment mode"
            PayKind.METHOD -> "payment method"
            PayKind.BANK -> "bank"
        }
        AlertDialog(
            onDismissRequest = { newTagKind = null },
            title = { Text("Add a " + what) },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text(what.replaceFirstChar { c -> c.uppercase() }) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTagName.isNotBlank(),
                    onClick = { vm.addPayTag(kind, newTagName.trim()); newTagKind = null }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { newTagKind = null }) { Text("Cancel") } }
        )
    }

    if (showStartPicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { startDate = keepTimeOfDay(it, startDate) }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = st) }
    }

    if (showEndPicker) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = endDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { endDate = DateRanges.endOfDay(it) }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = st) }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** The date picker returns UTC midnight; keep the clock time already chosen. */
private fun keepTimeOfDay(picked: Long, previous: Long): Long {
    val old = java.util.Calendar.getInstance().apply { timeInMillis = previous }
    return java.util.Calendar.getInstance().apply {
        timeInMillis = picked
        set(java.util.Calendar.HOUR_OF_DAY, old.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, old.get(java.util.Calendar.MINUTE))
    }.timeInMillis
}

private fun trimAmount(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", v)
    return if (s.endsWith(".00")) s.dropLast(3) else s
}
