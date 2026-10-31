package com.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.data.PayTag
import com.expensetracker.data.TxType
import com.expensetracker.data.TxView
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.charts.paletteFor
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

/** The single card shape used everywhere, so the app reads as one surface family. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        trailing()
    }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Income / expense / balance headline number. */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Month / week stepper with a tappable label. */
@Composable
fun PeriodStepper(
    label: String,
    canStep: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(Icons.Default.KeyboardArrowLeft, "Previous period", canStep, onPrev)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = Modifier.clickable(onClick = onLabelClick)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        StepButton(Icons.Default.KeyboardArrowRight, "Next period", canStep, onNext)
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

/** Rounded pill used for filters and quick choices. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val bg = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent
    val fg = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1
        )
    }
}

/** One ledger row: emoji chip, title, meta line, signed amount. */
@Composable
fun TxRow(
    v: TxView,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val income = v.tx.type == TxType.INCOME
    val transfer = v.tx.type == TxType.TRANSFER
    // A transfer is neither a gain nor a loss, so it gets a neutral treatment
    // rather than being coloured like spending.
    val amountColor = when {
        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        income -> positiveColor()
        else -> negativeColor()
    }
    val chipColor = when {
        transfer -> MaterialTheme.colorScheme.primary
        else -> v.categoryColor?.let { Color(it) } ?: paletteFor(v.tx.id.toInt())
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(chipColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    transfer -> "🔄"
                    else -> v.categoryEmoji ?: (if (income) "💰" else "💸")
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = primaryLabel(v),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = metaLabel(v),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (transfer) Fmt.money(currency, v.tx.amount)
            else Fmt.signed(currency, v.tx.amount, income),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            maxLines = 1
        )
    }
}

private fun primaryLabel(v: TxView): String = when {
    v.tx.type == TxType.TRANSFER -> "Transfer"
    v.tx.merchant.isNotBlank() -> v.tx.merchant
    v.categoryName != null -> v.categoryName
    v.tx.note.isNotBlank() -> v.tx.note
    else -> if (v.tx.type == TxType.INCOME) "Income" else "Expense"
}

private fun metaLabel(v: TxView): String {
    if (v.tx.type == TxType.TRANSFER) {
        val from = v.accountName ?: "?"
        val to = v.toAccountName ?: "?"
        return from + " → " + to
    }
    val bits = ArrayList<String>()
    v.categoryName?.let { if (it != primaryLabel(v)) bits.add(it) }
    v.personName?.let { bits.add("for " + it) }
    // Prefer the payment method over the account: it is the more specific of
    // the two and is what the user actually chose at the till.
    (v.methodName ?: v.accountName)?.let { bits.add(it) }
    v.bankName?.let { bits.add(it) }
    if (bits.isEmpty()) bits.add(com.expensetracker.data.DateRanges.timeOfDay(v.tx.date))
    return bits.joinToString(" · ")
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Day separator inside the transaction list. */
@Composable
fun DayHeader(label: String, total: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = total,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One labelled row of payment chips with a way to add a custom value.
 *
 * Shared by the transaction editor and the recurring-rule editor so a rule
 * records the same payment detail its postings will carry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayTagRow(
    label: String,
    tags: List<PayTag>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Caption(label)
        Text(
            text = "+ Add",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAdd)
        )
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { t -> Pill(t.name, selectedId == t.id, { onSelect(t.id) }) }
    }
}
