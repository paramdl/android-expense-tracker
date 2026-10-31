package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.Bucket
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.RangeMode
import com.expensetracker.ui.charts.ChartEmpty
import com.expensetracker.ui.charts.DonutChart
import com.expensetracker.ui.charts.DonutLegend
import com.expensetracker.ui.charts.GroupedBarChart
import com.expensetracker.ui.charts.ProgressMeter
import com.expensetracker.ui.charts.RankedBars
import com.expensetracker.ui.charts.Slice
import com.expensetracker.ui.charts.TrendLine
import com.expensetracker.ui.charts.paletteFor
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.PeriodStepper
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import com.expensetracker.ui.components.StatTile
import com.expensetracker.ui.components.TxRow
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenTx: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenBudgets: () -> Unit,
    contentPadding: PaddingValues
) {
    val dash by vm.dash.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()

    val pos = positiveColor()
    val neg = negativeColor()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PeriodStepper(
                label = period.label,
                canStep = vm.canShift(),
                onPrev = { vm.shiftPeriod(-1) },
                onNext = { vm.shiftPeriod(1) },
                onLabelClick = { vm.setRangeMode(RangeMode.MONTH) }
            )
        }

        item { RangePills(vm, period.mode) }

        if (pending.isNotEmpty() && com.expensetracker.BuildConfig.SMS_CAPTURE) {
            item { SmsBanner(pending.size, onOpenSms) }
        }

        item {
            BalanceCard(
                income = dash.totals.income,
                expense = dash.totals.expense,
                currency = currency,
                budget = budget,
                pos = pos,
                neg = neg
            )
        }

        item {
            SectionCard {
                SectionTitle("Where the money went") {
                    Text(
                        text = "Report",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenReports)
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (dash.expenseByCategory.isEmpty()) {
                    ChartEmpty("No spending recorded for this period yet.")
                } else {
                    val slices = dash.expenseByCategory.toSlices()
                    DonutChart(slices = slices) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Caption("Total spent")
                            Text(
                                text = Fmt.money(currency, dash.totals.expense),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Caption(dash.expenseByCategory.size.toString() + " categories")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    DonutLegend(slices, currency)
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Income vs spending")
                Spacer(Modifier.height(4.dp))
                Caption("Last 6 months")
                Spacer(Modifier.height(14.dp))
                if (dash.trend.all { it.second == 0.0 && it.third == 0.0 }) {
                    ChartEmpty("Add a few transactions to see the trend.")
                } else {
                    GroupedBarChart(
                        labels = dash.trend.map { it.first },
                        incomes = dash.trend.map { it.second },
                        expenses = dash.trend.map { it.third },
                        incomeColor = pos,
                        expenseColor = neg
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot("Earned", pos)
                        LegendDot("Spent", neg)
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Daily spending")
                Spacer(Modifier.height(4.dp))
                Caption("Last 30 days")
                Spacer(Modifier.height(14.dp))
                if (dash.daily.all { it.second == 0.0 }) {
                    ChartEmpty("Nothing spent in the last 30 days.")
                } else {
                    TrendLine(values = dash.daily.map { it.second }, lineColor = neg)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Caption(com.expensetracker.data.DateRanges.dayShort(dash.daily.first().first))
                        Caption(
                            "Peak " + Fmt.money(currency, dash.daily.maxOf { it.second })
                        )
                        Caption(com.expensetracker.data.DateRanges.dayShort(dash.daily.last().first))
                    }
                }
            }
        }

        if (dash.byPerson.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Spent on people")
                    Spacer(Modifier.height(14.dp))
                    RankedBars(items = dash.byPerson.toSlices(), currency = currency, max = 6)
                }
            }
        }

        if (dash.budgets.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Category budgets") {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenBudgets)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    dash.budgets.forEach { b ->
                        val barColor = when {
                            b.over -> neg
                            b.nearLimit -> Color(0xFFD1A73D)
                            else -> pos
                        }
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    b.category.emoji + "  " + b.category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    Fmt.money(currency, b.spent) + " / " + Fmt.money(currency, b.budget),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            ProgressMeter(fraction = b.usedFraction, color = barColor)
                            if (b.over) {
                                Spacer(Modifier.height(4.dp))
                                Caption("Over by " + Fmt.money(currency, -b.remaining))
                            }
                        }
                    }
                }
            }
        }

        // Always shown. Previously this appeared only once a rule existed, so a
        // new user had no way to discover recurring transactions from the
        // dashboard at all.
        item {
            if (upcoming.isNotEmpty()) {
                UpcomingCard(upcoming, currency, onOpenRecurring)
            } else {
                SectionCard {
                    SectionTitle("Recurring") {
                        Text(
                            text = "Set up",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenRecurring)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "Add an EMI, a monthly subscription or an annual renewal once and it " +
                            "records itself each time it falls due."
                    )
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Recent") {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onSeeAll)
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (dash.recent.isEmpty()) {
                    ChartEmpty("Tap + to add your first transaction.")
                } else {
                    dash.recent.forEachIndexed { i, v ->
                        TxRow(v = v, currency = currency, onClick = { onOpenTx(v.tx.id) })
                        if (i != dash.recent.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun RangePills(vm: AppViewModel, active: RangeMode) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Week", active == RangeMode.WEEK, { vm.setRangeMode(RangeMode.WEEK) })
        Pill("Month", active == RangeMode.MONTH, { vm.setRangeMode(RangeMode.MONTH) })
        Pill("Year", active == RangeMode.YEAR, { vm.setRangeMode(RangeMode.YEAR) })
        Pill("All", active == RangeMode.ALL, { vm.setRangeMode(RangeMode.ALL) })
    }
}

@Composable
private fun BalanceCard(
    income: Double,
    expense: Double,
    currency: String,
    budget: Double,
    pos: Color,
    neg: Color
) {
    val balance = income - expense
    SectionCard {
        Caption("Balance this period")
        Spacer(Modifier.height(4.dp))
        Text(
            text = Fmt.money(currency, balance),
            style = MaterialTheme.typography.displaySmall,
            color = if (balance >= 0) pos else neg,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            StatTile("EARNED", Fmt.money(currency, income), pos, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatTile("SPENT", Fmt.money(currency, expense), neg, Modifier.weight(1f))
        }
        if (budget > 0) {
            Spacer(Modifier.height(18.dp))
            val frac = (expense / budget).toFloat()
            val over = expense > budget
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caption("Monthly budget")
                Caption(
                    if (over) "Over by " + Fmt.money(currency, expense - budget)
                    else Fmt.money(currency, budget - expense) + " left"
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressMeter(fraction = frac, color = if (over) neg else pos)
        }
    }
}

@Composable
private fun SmsBanner(count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📩", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = count.toString() + " message" + (if (count == 1) "" else "s") + " to review",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Bank alerts detected and ready to add",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Text(
                text = "Review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}

/** Buckets carry their own colour when they came from a category; otherwise use the palette. */
fun List<Bucket>.toSlices(): List<Slice> = mapIndexed { i, b ->
    Slice(
        label = b.label,
        value = b.amount,
        color = if (b.colorArgb != 0) Color(b.colorArgb) else paletteFor(i),
        emoji = b.emoji
    )
}
