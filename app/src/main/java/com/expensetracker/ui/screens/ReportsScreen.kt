package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.RangeMode
import com.expensetracker.ui.charts.ChartEmpty
import com.expensetracker.ui.charts.DonutChart
import com.expensetracker.ui.charts.DonutLegend
import com.expensetracker.ui.charts.GroupedBarChart
import com.expensetracker.ui.charts.RankedBars
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.PeriodStepper
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import com.expensetracker.ui.components.StatTile
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

@Composable
fun ReportsScreen(
    vm: AppViewModel,
    onExportCsv: () -> Unit,
    contentPadding: PaddingValues
) {
    val dash by vm.dash.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()

    var breakdown by remember { mutableStateOf(Breakdown.CATEGORY) }
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
                onLabelClick = {}
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Week", period.mode == RangeMode.WEEK, { vm.setRangeMode(RangeMode.WEEK) })
                Pill("Month", period.mode == RangeMode.MONTH, { vm.setRangeMode(RangeMode.MONTH) })
                Pill("Year", period.mode == RangeMode.YEAR, { vm.setRangeMode(RangeMode.YEAR) })
                Pill("All", period.mode == RangeMode.ALL, { vm.setRangeMode(RangeMode.ALL) })
            }
        }

        // ------------------------------------------------------------ summary
        item {
            SectionCard {
                SectionTitle("Summary")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatTile("EARNED", Fmt.money(currency, dash.totals.income), pos, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    StatTile("SPENT", Fmt.money(currency, dash.totals.expense), neg, Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))

                val net = dash.totals.balance
                val savingsRate = if (dash.totals.income > 0) net / dash.totals.income * 100.0 else 0.0
                StatRow("Net saved", Fmt.money(currency, net), if (net >= 0) pos else neg)
                Spacer(Modifier.height(10.dp))
                StatRow(
                    "Savings rate",
                    if (dash.totals.income > 0) String.format("%.0f%%", savingsRate) else "—",
                    MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                StatRow(
                    "Largest category",
                    dash.expenseByCategory.firstOrNull()?.label ?: "—",
                    MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                StatRow(
                    "Average per day",
                    if (dash.daily.isNotEmpty())
                        Fmt.money(currency, dash.daily.sumOf { it.second } / dash.daily.size)
                    else "—",
                    MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ------------------------------------------------------- breakdown tab
        item {
            SectionCard {
                SectionTitle("Spending breakdown")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Category", breakdown == Breakdown.CATEGORY, { breakdown = Breakdown.CATEGORY })
                    Pill("Person", breakdown == Breakdown.PERSON, { breakdown = Breakdown.PERSON })
                    Pill("Account", breakdown == Breakdown.ACCOUNT, { breakdown = Breakdown.ACCOUNT })
                }
                Spacer(Modifier.height(18.dp))

                val buckets = when (breakdown) {
                    Breakdown.CATEGORY -> dash.expenseByCategory
                    Breakdown.PERSON -> dash.byPerson
                    Breakdown.ACCOUNT -> dash.expenseByAccount
                }
                if (buckets.isEmpty()) {
                    ChartEmpty(
                        when (breakdown) {
                            Breakdown.PERSON -> "No transactions are tagged to a person yet."
                            Breakdown.ACCOUNT -> "No transactions are assigned to an account yet."
                            else -> "No spending in this period."
                        }
                    )
                } else {
                    val slices = buckets.toSlices()
                    DonutChart(slices = slices) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Caption("Total")
                            Text(
                                text = Fmt.money(currency, buckets.sumOf { it.amount }),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    DonutLegend(slices, currency, max = 10)
                }
            }
        }

        // ----------------------------------------------------------- ranked
        item {
            SectionCard {
                SectionTitle("Top categories")
                Spacer(Modifier.height(16.dp))
                if (dash.expenseByCategory.isEmpty()) {
                    ChartEmpty("Nothing to rank yet.")
                } else {
                    RankedBars(dash.expenseByCategory.toSlices(), currency, max = 8)
                }
            }
        }

        if (dash.topMerchants.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Where you shop most")
                    Spacer(Modifier.height(16.dp))
                    RankedBars(dash.topMerchants.toSlices(), currency, max = 8)
                }
            }
        }

        // ------------------------------------------------------------- trend
        item {
            SectionCard {
                SectionTitle("6-month trend")
                Spacer(Modifier.height(16.dp))
                if (dash.trend.all { it.second == 0.0 && it.third == 0.0 }) {
                    ChartEmpty("Not enough history yet.")
                } else {
                    GroupedBarChart(
                        labels = dash.trend.map { it.first },
                        incomes = dash.trend.map { it.second },
                        expenses = dash.trend.map { it.third },
                        incomeColor = pos,
                        expenseColor = neg,
                        height = 190
                    )
                    Spacer(Modifier.height(16.dp))
                    dash.trend.reversed().forEach { (label, inc, exp) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Row {
                                Text(
                                    "+" + Fmt.compact(inc),
                                    style = MaterialTheme.typography.bodyMedium, color = pos
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "-" + Fmt.compact(exp),
                                    style = MaterialTheme.typography.bodyMedium, color = neg
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    Fmt.compact(inc - exp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (inc - exp >= 0) pos else neg
                                )
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------- balances
        if (dash.accountBalances.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Account balances")
                    Spacer(Modifier.height(4.dp))
                    Caption("Opening balance plus every transaction ever recorded")
                    Spacer(Modifier.height(14.dp))
                    dash.accountBalances.forEach { (acc, bal) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                acc.emoji + "  " + acc.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                Fmt.money(currency, bal),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (bal >= 0) MaterialTheme.colorScheme.onSurface else neg
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------- lifetime
        item {
            SectionCard {
                SectionTitle("All time")
                Spacer(Modifier.height(14.dp))
                StatRow("Total earned", Fmt.money(currency, dash.allTimeTotals.income), pos)
                Spacer(Modifier.height(10.dp))
                StatRow("Total spent", Fmt.money(currency, dash.allTimeTotals.expense), neg)
                Spacer(Modifier.height(10.dp))
                StatRow(
                    "Net worth change",
                    Fmt.money(currency, dash.allTimeTotals.balance),
                    if (dash.allTimeTotals.balance >= 0) pos else neg
                )
                Spacer(Modifier.height(10.dp))
                StatRow(
                    "Transactions recorded",
                    dash.txCount.toString(),
                    MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Export this period as CSV")
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

private enum class Breakdown { CATEGORY, PERSON, ACCOUNT }

@Composable
private fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}
