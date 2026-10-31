package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.Category
import com.expensetracker.data.Calc
import com.expensetracker.data.DateRanges
import com.expensetracker.data.PayKind
import com.expensetracker.data.PayTag
import com.expensetracker.data.Transaction
import com.expensetracker.data.TxSource
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    vm: AppViewModel,
    txId: Long,
    onDone: () -> Unit
) {
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(txId == 0L) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    // TextFieldValue rather than a plain String: the calculator keys insert
    // characters, and without an explicit cursor the field and the keys disagree
    // about where "next" is (tapping + then typing produced "450120+").
    var amountField by remember { mutableStateOf(TextFieldValue("")) }
    val amountText = amountField.text
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var personId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var modeId by remember { mutableStateOf<Long?>(null) }
    var methodId by remember { mutableStateOf<Long?>(null) }
    var bankId by remember { mutableStateOf<Long?>(null) }
    var newTagKind by remember { mutableStateOf<PayKind?>(null) }
    var newTagName by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(TxSource.MANUAL) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    var merchantIdeas by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(merchant) {
        vm.merchantSuggestions(merchant) { merchantIdeas = it }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showNewPerson by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    // Load the existing row once when editing.
    LaunchedEffect(txId) {
        if (txId != 0L) {
            vm.txById(txId) { t ->
                if (t != null) {
                    type = t.type
                    amountField = TextFieldValue(trimAmount(t.amount))
                    date = t.date
                    categoryId = t.categoryId
                    personId = t.personId
                    accountId = t.accountId
                    toAccountId = t.toAccountId
                    modeId = t.modeId; methodId = t.methodId; bankId = t.bankId
                    merchant = t.merchant
                    note = t.note
                    source = t.source
                    createdAt = t.createdAt
                }
                loaded = true
            }
        }
    }

    val accent = if (type == TxType.INCOME) positiveColor() else negativeColor()
    val categories = if (type == TxType.INCOME) lookups.incomeCategories else lookups.expenseCategories

    // A category from the other side of the ledger must not survive a type flip.
    LaunchedEffect(type) {
        if (categoryId != null && categories.none { it.id == categoryId }) categoryId = null
    }

    // The field doubles as a calculator: "450+120*2" is a valid amount.
    val amount = Calc.eval(amountText) ?: 0.0
    val canSave = amount > 0.0 && (
        type != TxType.TRANSFER ||
            (accountId != null && toAccountId != null && accountId != toAccountId)
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (txId == 0L) "New transaction" else "Edit transaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (txId != 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        if (!loaded) return@Scaffold

        LazyColumn(
            // enableEdgeToEdge() stops the window resizing for the keyboard, so the
            // list has to consume the IME inset itself or the focused field ends up
            // hidden behind the keyboard.
            modifier = Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "💸  Expense", type == TxType.EXPENSE,
                        { type = TxType.EXPENSE }, Modifier.weight(1f), negativeColor()
                    )
                    Pill(
                        "💰  Income", type == TxType.INCOME,
                        { type = TxType.INCOME }, Modifier.weight(1f), positiveColor()
                    )
                    Pill(
                        "🔄  Transfer", type == TxType.TRANSFER,
                        { type = TxType.TRANSFER }, Modifier.weight(1f),
                        MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                SectionCard {
                    Caption(
                        when (type) {
                            TxType.INCOME -> "Amount received"
                            TxType.TRANSFER -> "Amount to move"
                            else -> "Amount spent"
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currency,
                            style = MaterialTheme.typography.displaySmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = amountField,
                            onValueChange = { v ->
                                // Digits, a decimal point, and simple arithmetic.
                                val cleaned = v.text.filter { it.isDigit() || it in ".+-*/()" }
                                amountField =
                                    if (cleaned == v.text) v
                                    else TextFieldValue(cleaned, TextRange(cleaned.length))
                            },
                            placeholder = {
                                Text(
                                    "0",
                                    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // The numeric keypad has no operator keys, so the calculator
                    // needs its own row or it cannot be reached at all.
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("+", "−", "×", "÷").forEach { op ->
                            CalcKey(op, Modifier.weight(1f)) {
                                val ch = when (op) {
                                    "−" -> "-"
                                    "×" -> "*"
                                    "÷" -> "/"
                                    else -> op
                                }
                                amountField = appended(amountField, ch)
                            }
                        }
                        CalcKey("⌫", Modifier.weight(1f)) {
                            val t = amountField.text
                            if (t.isNotEmpty()) {
                                val next = t.dropLast(1)
                                amountField = TextFieldValue(next, TextRange(next.length))
                            }
                        }
                    }

                    // Only shown while the field holds maths, so a plain amount
                    // is not cluttered by an echo of itself.
                    if (Calc.isExpression(amountText)) {
                        Spacer(Modifier.height(8.dp))
                        Caption(
                            if (amount > 0) "= " + Fmt.money2(currency, amount)
                            else "Not a valid calculation yet"
                        )
                    }
                }
            }

            if (type != TxType.TRANSFER) {
                item {
                    SectionCard {
                        SectionTitle("Category")
                        Spacer(Modifier.height(12.dp))
                        CategoryGrid(
                            categories = categories,
                            selectedId = categoryId,
                            onSelect = { categoryId = if (categoryId == it) null else it }
                        )
                    }
                }
            }

            if (type != TxType.TRANSFER) {
                item {
                SectionCard {
                    SectionTitle("Person") {
                        Text(
                            text = "+ New",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                newPersonName = ""; showNewPerson = true
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        if (type == TxType.INCOME) "Who did this money come from?"
                        else "Who was this spent on?"
                    )
                    Spacer(Modifier.height(12.dp))
                    if (lookups.people.isEmpty()) {
                        Caption("No people yet — tap “+ New” to add one.")
                    } else {
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
                }
                }
            }

            item {
                SectionCard {
                    SectionTitle(if (type == TxType.TRANSFER) "From account" else "Account")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lookups.accounts.forEach { a ->
                            Pill(a.emoji + "  " + a.name, accountId == a.id, {
                                accountId = if (accountId == a.id) null else a.id
                            })
                        }
                    }

                    if (type == TxType.TRANSFER) {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("To account")
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
                        if (accountId != null && toAccountId != null) {
                            Spacer(Modifier.height(10.dp))
                            Caption(
                                "Moves " + Fmt.money(currency, amount) + " between your own " +
                                    "accounts. It changes both balances but is left out of " +
                                    "income, spending and category totals."
                            )
                        }
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitle("How it was paid")
                    Spacer(Modifier.height(4.dp))
                    Caption("Optional. Tap “+ Add” on any row to record your own.")

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

            item {
                SectionCard {
                    SectionTitle("Details")
                    Spacer(Modifier.height(12.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDatePicker = true }
                            .padding(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = DateRanges.dayFullLabel(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text(if (type == TxType.INCOME) "Source / payer" else "Merchant / shop") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Names already used, so a regular shop is one tap rather
                    // than retyped every time.
                    if (merchantIdeas.isNotEmpty() && merchant != merchantIdeas.firstOrNull()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                            merchantIdeas.forEach { m ->
                                Pill(m, merchant.equals(m, true), { merchant = m })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        vm.saveTx(
                            Transaction(
                                id = txId,
                                type = type,
                                amount = amount,
                                date = date,
                                // A transfer carries neither category nor person.
                                categoryId = if (type == TxType.TRANSFER) null else categoryId,
                                personId = if (type == TxType.TRANSFER) null else personId,
                                accountId = accountId,
                                toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                                modeId = modeId, methodId = methodId, bankId = bankId,
                                merchant = merchant.trim(),
                                note = note.trim(),
                                source = source,
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
                        if (txId == 0L) "Add transaction" else "Save changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        // The picker returns UTC midnight; keep the original clock time.
                        val old = Calendar.getInstance().apply { timeInMillis = date }
                        val c = Calendar.getInstance().apply {
                            timeInMillis = picked
                            set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                        }
                        date = c.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    if (showNewPerson) {
        AlertDialog(
            onDismissRequest = { showNewPerson = false },
            title = { Text("Add person") },
            text = {
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPersonName.isNotBlank(),
                    onClick = {
                        vm.savePerson(com.expensetracker.data.Person(name = newPersonName.trim()))
                        showNewPerson = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPerson = false }) { Text("Cancel") }
            }
        )
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTx(txId); showDeleteConfirm = false; onDone()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/** Appends [ch] and parks the cursor after it, so typing continues from the end. */
private fun appended(current: TextFieldValue, ch: String): TextFieldValue {
    val next = current.text + ch
    return TextFieldValue(next, TextRange(next.length))
}

/** One key of the inline calculator strip under the amount field. */
@Composable
private fun CalcKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Wrapping grid of category chips, three per row. */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    CategoryChip(
                        category = c,
                        selected = selectedId == c.id,
                        onClick = { onSelect(c.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = Color(category.colorArgb)
    val bg = if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) tint else Color.Transparent
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (selected) Modifier.selectedOutline(border) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(category.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

private fun Modifier.selectedOutline(color: Color): Modifier =
    this.border(width = 1.5.dp, color = color, shape = RoundedCornerShape(14.dp))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun transparentFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

private fun trimAmount(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", v)
    return if (s.endsWith(".00")) s.dropLast(3) else s
}
