package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.DateRanges
import com.expensetracker.data.EndMode
import com.expensetracker.data.Recurrence
import com.expensetracker.data.RecurringRule
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.charts.ProgressMeter
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<RecurringRule?>(null) }

    val active = rules.filter { it.active }
    val finished = rules.filter { !it.active }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                text = { Text("New") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🔁",
                        title = "No recurring transactions",
                        body = "Set up an EMI, a monthly subscription or an annual renewal once, " +
                            "and it will be recorded automatically each time it is due."
                    )
                }
            }

            if (active.isNotEmpty()) {
                item { Caption("ACTIVE") }
                items(active, key = { it.id }) { r ->
                    RuleCard(
                        rule = r,
                        currency = currency,
                        onClick = { onEdit(r.id) },
                        onToggle = { vm.setRuleActive(r.id, false) },
                        onDelete = { confirmDelete = r }
                    )
                }
            }

            if (finished.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Caption("PAUSED & COMPLETED")
                }
                items(finished, key = { it.id }) { r ->
                    RuleCard(
                        rule = r,
                        currency = currency,
                        onClick = { onEdit(r.id) },
                        onToggle = { vm.setRuleActive(r.id, true) },
                        onDelete = { confirmDelete = r }
                    )
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete " + r.name + "?") },
            text = {
                Text(
                    "You can keep the " + r.generatedCount + " transaction" +
                        (if (r.generatedCount == 1) "" else "s") +
                        " it has already recorded, or remove those too."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRule(r.id, alsoDeletePosted = false); confirmDelete = null
                }) { Text("Keep transactions") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        vm.deleteRule(r.id, alsoDeletePosted = true); confirmDelete = null
                    }) {
                        Text("Delete all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RecurringRule,
    currency: String,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val income = rule.type == TxType.INCOME
    val savings = rule.type == TxType.TRANSFER
    val accent = when {
        income -> positiveColor()
        savings -> MaterialTheme.colorScheme.primary
        else -> negativeColor()
    }
    val next = Recurrence.nextDue(rule)

    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        income -> "💰"
                        savings -> "🐷"
                        else -> "🔁"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Caption(Recurrence.describe(rule) + " · " + Recurrence.describeEnd(rule))
            }
            Text(
                // Savings moves money between the user's own accounts, so it
                // carries no +/- sign: nothing is earned and nothing is spent.
                if (savings) Fmt.money(currency, rule.amount)
                else Fmt.signed(currency, rule.amount, income),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        // Progress bar makes an EMI's remaining term obvious at a glance.
        if (rule.endMode == EndMode.AFTER_N && (rule.totalCount ?: 0) > 0) {
            Spacer(Modifier.height(12.dp))
            ProgressMeter(
                fraction = rule.generatedCount.toFloat() / (rule.totalCount ?: 1),
                color = accent
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caption(rule.remaining.toString() + " left")
                Caption("Total " + Fmt.money(currency, rule.amount * (rule.totalCount ?: 0)))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Caption(
                when {
                    !rule.active && next == null -> "Completed"
                    !rule.active -> "Paused"
                    next != null -> "Next on " + DateRanges.dayShort(next)
                    else -> "Completed"
                }
            )
            Row {
                TextButton(onClick = onToggle) {
                    Text(if (rule.active) "Pause" else "Resume")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Compact upcoming list for the dashboard. */
@Composable
fun UpcomingCard(
    upcoming: List<Pair<RecurringRule, Long>>,
    currency: String,
    onOpen: () -> Unit
) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Coming up", style = MaterialTheme.typography.titleMedium)
            Text(
                "Manage",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpen)
            )
        }
        Spacer(Modifier.height(12.dp))
        upcoming.forEach { (rule, due) ->
            val income = rule.type == TxType.INCOME
            val savings = rule.type == TxType.TRANSFER
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Caption(DateRanges.dayShort(due) + " · " + Recurrence.describe(rule))
                }
                Text(
                    if (savings) Fmt.money(currency, rule.amount)
                    else Fmt.signed(currency, rule.amount, income),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        income -> positiveColor()
                        savings -> MaterialTheme.colorScheme.primary
                        else -> negativeColor()
                    }
                )
            }
        }
    }
}

@Suppress("unused")
private val unusedColor: Color = Color.Transparent
