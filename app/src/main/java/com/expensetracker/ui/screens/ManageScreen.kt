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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.Account
import com.expensetracker.data.Category
import com.expensetracker.data.Person
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.charts.ChartPalette
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.SectionCard

private val EMOJI_CHOICES = listOf(
    "🛍️", "🏠", "🛒", "🍽️", "⛽", "💡", "🏡", "🏥", "🎓", "🎬", "✈️", "👕",
    "💇", "📱", "💳", "🛡️", "📈", "🎁", "🔧", "🐾", "🤝", "💸", "💼", "🧾",
    "💰", "↩️", "💵", "🏦", "🪪", "📲", "🚕", "☕", "📚", "🎮", "🧾", "📁"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageScreen(
    vm: AppViewModel,
    kind: String,
    onBack: () -> Unit
) {
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()

    var editCategory by remember { mutableStateOf<Category?>(null) }
    var editPerson by remember { mutableStateOf<Person?>(null) }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val title = when (kind) {
        "people" -> "People"
        "accounts" -> "Accounts"
        else -> "Categories"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (kind) {
                    "people" -> editPerson = Person(name = "")
                    "accounts" -> editAccount = Account(name = "")
                    else -> editCategory = Category(name = "", type = TxType.EXPENSE)
                }
            }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (kind) {
                "people" -> {
                    item { Caption("Tag a transaction to a person to see who your money goes on.") }
                    items(lookups.people, key = { it.id }) { p ->
                        ManageRow(
                            emoji = "👤",
                            title = p.name,
                            subtitle = p.note.ifBlank { "Tap to rename" },
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { editPerson = p },
                            onDelete = { confirmDelete = "person" to p.id }
                        )
                    }
                }
                "accounts" -> {
                    item { Caption("Cash, bank accounts, cards and wallets money moves through.") }
                    items(lookups.accounts, key = { it.id }) { a ->
                        ManageRow(
                            emoji = a.emoji,
                            title = a.name,
                            subtitle = "Opening balance " + Fmt.money(currency, a.openingBalance),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { editAccount = a },
                            onDelete = { confirmDelete = "account" to a.id }
                        )
                    }
                }
                else -> {
                    item { Caption("EXPENSE") }
                    items(lookups.expenseCategories, key = { it.id }) { c ->
                        ManageRow(
                            emoji = c.emoji,
                            title = c.name,
                            subtitle = if (c.budget > 0)
                                "Budget " + Fmt.money(currency, c.budget) + " a month"
                            else "Expense category",
                            tint = Color(c.colorArgb),
                            onClick = { editCategory = c },
                            onDelete = { confirmDelete = "category" to c.id }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)); Caption("INCOME") }
                    items(lookups.incomeCategories, key = { it.id }) { c ->
                        ManageRow(
                            emoji = c.emoji,
                            title = c.name,
                            subtitle = "Income category",
                            tint = Color(c.colorArgb),
                            onClick = { editCategory = c },
                            onDelete = { confirmDelete = "category" to c.id }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }

    editCategory?.let { cat ->
        CategoryDialog(
            initial = cat,
            onDismiss = { editCategory = null },
            onSave = { vm.saveCategory(it); editCategory = null }
        )
    }

    editPerson?.let { p ->
        SimpleNameDialog(
            title = if (p.id == 0L) "Add person" else "Edit person",
            initialName = p.name,
            initialNote = p.note,
            noteLabel = "Note (optional)",
            onDismiss = { editPerson = null },
            onSave = { name, note ->
                vm.savePerson(p.copy(name = name, note = note)); editPerson = null
            }
        )
    }

    editAccount?.let { a ->
        AccountDialog(
            initial = a,
            currency = currency,
            onDismiss = { editAccount = null },
            onSave = { vm.saveAccount(it); editAccount = null }
        )
    }

    confirmDelete?.let { (kindName, id) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this " + kindName + "?") },
            text = {
                Text(
                    "Transactions using it are kept, but they lose this " + kindName + " tag."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (kindName) {
                        "person" -> vm.deletePerson(id)
                        "account" -> vm.deleteAccount(id)
                        else -> vm.deleteCategory(id)
                    }
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ManageRow(
    emoji: String,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Caption(subtitle)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var type by remember { mutableStateOf(initial.type) }
    var color by remember { mutableStateOf(initial.colorArgb) }
    var budget by remember {
        mutableStateOf(if (initial.budget > 0) initial.budget.toInt().toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Caption("TYPE")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Expense", type == TxType.EXPENSE, { type = TxType.EXPENSE })
                    Pill("Income", type == TxType.INCOME, { type = TxType.INCOME })
                }
                if (type == TxType.EXPENSE) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { v -> budget = v.filter { it.isDigit() }.take(9) },
                        label = { Text("Monthly budget (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(14.dp))
                Caption("ICON")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    EMOJI_CHOICES.distinct().forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (emoji == e) Color(color).copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Caption("COLOUR")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    ChartPalette.forEach { c ->
                        val argb = c.value.toLong().let { android.graphics.Color.argb(
                            (c.alpha * 255).toInt(), (c.red * 255).toInt(),
                            (c.green * 255).toInt(), (c.blue * 255).toInt()
                        ) }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(c)
                                .clickable { color = argb }
                        ) {
                            if (color == argb) {
                                Box(
                                    Modifier.fillMaxSize().padding(9.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), emoji = emoji, type = type, colorArgb = color,
                            budget = if (type == TxType.EXPENSE) (budget.toDoubleOrNull() ?: 0.0) else 0.0
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AccountDialog(
    initial: Account,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var opening by remember {
        mutableStateOf(if (initial.openingBalance != 0.0) initial.openingBalance.toInt().toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = opening,
                    onValueChange = { v -> opening = v.filter { it.isDigit() || it == '-' } },
                    label = { Text("Opening balance (" + currency + ")") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Caption("ICON")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    listOf("🏦", "💵", "💳", "🪪", "📲", "🏧", "💰", "📈").forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), emoji = emoji,
                            openingBalance = opening.toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SimpleNameDialog(
    title: String,
    initialName: String,
    initialNote: String,
    noteLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(noteLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), note.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
