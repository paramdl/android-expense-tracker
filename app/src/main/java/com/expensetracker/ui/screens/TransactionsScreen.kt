package com.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.DateRanges
import com.expensetracker.data.TxType
import com.expensetracker.data.TxView
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.DayHeader
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.PeriodStepper
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.TxRow
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

@Composable
fun TransactionsScreen(
    vm: AppViewModel,
    onOpenTx: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    val list by vm.txList.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val truncated by vm.ledgerTruncated.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }

    // The ledger query is skipped while this tab is off screen, so tell the
    // ViewModel when it is actually being shown.
    DisposableEffect(Unit) {
        vm.setLedgerActive(true)
        onDispose { vm.setLedgerActive(false) }
    }

    val income = list.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.amount }
    val expense = list.filter { it.tx.type == TxType.EXPENSE }.sumOf { it.tx.amount }
    val grouped = remember(list) { list.groupBy { DateRanges.startOfDay(it.tx.date) } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            if (filter.usePeriod) {
                PeriodStepper(
                    label = period.label,
                    canStep = vm.canShift(),
                    onPrev = { vm.shiftPeriod(-1) },
                    onNext = { vm.shiftPeriod(1) },
                    onLabelClick = {}
                )
            } else {
                Text(
                    "Searching all time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { vm.setFilter(filter.copy(query = it)) },
                placeholder = { Text("Search merchant, note, person, amount") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setFilter(filter.copy(query = "")) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "Filters",
                                tint = if (filter.activeCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        item {
            AnimatedVisibility(visible = showFilters) {
                FilterPanel(vm)
            }
        }

        item {
            SectionCard(padding = PaddingValues(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Caption(
                            list.size.toString() +
                                (if (list.size == 1) " transaction" else " transactions")
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Fmt.money(currency, income - expense),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (income >= expense) positiveColor() else negativeColor()
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "+" + Fmt.money(currency, income),
                            style = MaterialTheme.typography.bodyMedium,
                            color = positiveColor()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "-" + Fmt.money(currency, expense),
                            style = MaterialTheme.typography.bodyMedium,
                            color = negativeColor()
                        )
                    }
                }
            }
        }

        if (list.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🧾",
                    title = "Nothing here yet",
                    body = if (filter.activeCount > 0)
                        "No transactions match these filters. Try clearing them."
                    else "Tap the + button to record your first transaction."
                )
            }
        } else {
            grouped.forEach { (day, rows) ->
                item(key = "h" + day) {
                    // Transfers move money sideways, so they must not tilt the
                    // day's total the way the summary above already excludes them.
                    val dayTotal = rows.sumOf {
                        when (it.tx.type) {
                            TxType.INCOME -> it.tx.amount
                            TxType.EXPENSE -> -it.tx.amount
                            TxType.TRANSFER -> 0.0
                        }
                    }
                    DayHeader(
                        label = DateRanges.relativeDay(day),
                        total = Fmt.signed(currency, kotlin.math.abs(dayTotal), dayTotal >= 0)
                    )
                }
                items(rows, key = { it.tx.id }) { v: TxView ->
                    TxRow(v = v, currency = currency, onClick = { onOpenTx(v.tx.id) })
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (truncated) {
                item {
                    Caption(
                        "Showing the most recent " + com.expensetracker.ui.AppViewModel.LEDGER_PAGE +
                            " transactions. Narrow the period, or search, to see the rest.",
                        Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FilterPanel(vm: AppViewModel) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val lookups by vm.lookups.collectAsStateWithLifecycle()

    SectionCard(padding = PaddingValues(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            if (filter.activeCount > 0 || !filter.usePeriod) {
                Text(
                    "Clear all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { vm.clearFilter() }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Caption("TYPE")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("All", filter.type == null, { vm.setFilter(filter.copy(type = null)) })
            Pill("Expense", filter.type == TxType.EXPENSE, {
                vm.setFilter(filter.copy(type = TxType.EXPENSE))
            }, accent = negativeColor())
            Pill("Income", filter.type == TxType.INCOME, {
                vm.setFilter(filter.copy(type = TxType.INCOME))
            }, accent = positiveColor())
        }

        Spacer(Modifier.height(14.dp))
        Caption("DATE RANGE")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Selected period", filter.usePeriod, { vm.setFilter(filter.copy(usePeriod = true)) })
            Pill("All time", !filter.usePeriod, { vm.setFilter(filter.copy(usePeriod = false)) })
        }

        if (lookups.categories.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Caption("CATEGORY")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                lookups.categories.forEach { c ->
                    Pill(c.emoji + "  " + c.name, filter.categoryId == c.id, {
                        vm.setFilter(
                            filter.copy(categoryId = if (filter.categoryId == c.id) null else c.id)
                        )
                    })
                }
            }
        }

        if (lookups.people.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Caption("PERSON")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                lookups.people.forEach { p ->
                    Pill(p.name, filter.personId == p.id, {
                        vm.setFilter(filter.copy(personId = if (filter.personId == p.id) null else p.id))
                    })
                }
            }
        }

        if (lookups.accounts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Caption("ACCOUNT")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                lookups.accounts.forEach { a ->
                    Pill(a.emoji + "  " + a.name, filter.accountId == a.id, {
                        vm.setFilter(filter.copy(accountId = if (filter.accountId == a.id) null else a.id))
                    })
                }
            }
        }
    }
}
