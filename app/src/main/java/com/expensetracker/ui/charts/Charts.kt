package com.expensetracker.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.Fmt
import androidx.compose.foundation.background as bg

data class Slice(val label: String, val value: Double, val color: Color, val emoji: String = "")

/**
 * Donut breakdown. Slices below 2% of the total are folded into a single
 * "Other" arc so the ring stays readable.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    thickness: Float = 34f,
    center: @Composable () -> Unit = {}
) {
    val total = slices.sumOf { it.value }.toFloat()
    val progress by animateFloatAsState(
        targetValue = if (total > 0f) 1f else 0f,
        animationSpec = tween(700),
        label = "donut"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(196.dp)) {
            val d = minOf(size.width, size.height) - thickness
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            val stroke = Stroke(width = thickness)

            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke
            )
            if (total <= 0f) return@Canvas

            var start = -90f
            val gap = if (slices.size > 1) 1.6f else 0f
            slices.forEach { s ->
                val sweep = (s.value.toFloat() / total) * 360f * progress
                if (sweep > 0.2f) {
                    drawArc(
                        color = s.color,
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.4f),
                        useCenter = false,
                        topLeft = topLeft, size = arcSize, style = stroke
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** Legend rows under a donut: colour dot, label, amount and share of total. */
@Composable
fun DonutLegend(
    slices: List<Slice>,
    currency: String,
    modifier: Modifier = Modifier,
    max: Int = 6
) {
    val total = slices.sumOf { it.value }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.take(max).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).bg(s.color))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = (if (s.emoji.isNotEmpty()) s.emoji + "  " else "") + s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Fmt.money(currency, s.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Fmt.percent(s.value, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp)
                )
            }
        }
        if (slices.size > max) {
            Text(
                text = "+" + (slices.size - max) + " more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Side-by-side income / expense bars, one pair per period.
 */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    incomes: List<Double>,
    expenses: List<Double>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    height: Int = 172
) {
    val max = (incomes + expenses).maxOrNull()?.toFloat() ?: 0f
    val progress by animateFloatAsState(
        targetValue = if (max > 0f) 1f else 0f, animationSpec = tween(700), label = "bars"
    )
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val n = labels.size
            if (n == 0) return@Canvas
            val slotW = size.width / n
            val barW = (slotW * 0.28f).coerceAtMost(20f)
            val gap = slotW * 0.08f
            val baseline = size.height

            // Three faint gridlines give the eye a scale without axis labels.
            for (i in 1..3) {
                val y = baseline - baseline * (i / 4f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            labels.indices.forEach { i ->
                val cx = slotW * i + slotW / 2f
                val incH = if (max > 0f) (incomes[i].toFloat() / max) * baseline * 0.94f * progress else 0f
                val expH = if (max > 0f) (expenses[i].toFloat() / max) * baseline * 0.94f * progress else 0f
                val r = androidx.compose.ui.geometry.CornerRadius(barW / 2.6f, barW / 2.6f)

                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(cx - barW - gap / 2f, baseline - incH),
                    size = Size(barW, incH.coerceAtLeast(1.5f)),
                    cornerRadius = r
                )
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(cx + gap / 2f, baseline - expH),
                    size = Size(barW, expH.coerceAtLeast(1.5f)),
                    cornerRadius = r
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(
                    text = l,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Ranked horizontal bars for category / person / merchant breakdowns. */
@Composable
fun RankedBars(
    items: List<Slice>,
    currency: String,
    modifier: Modifier = Modifier,
    max: Int = 8
) {
    val top = items.take(max)
    val peak = top.maxOfOrNull { it.value } ?: 0.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        top.forEach { s ->
            val frac by animateFloatAsState(
                targetValue = if (peak > 0) (s.value / peak).toFloat() else 0f,
                animationSpec = tween(600), label = "rank"
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (s.emoji.isNotEmpty()) s.emoji + "  " else "") + s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Fmt.money(currency, s.value),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        .bg(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(8.dp)
                            .clip(CircleShape).bg(s.color)
                    )
                }
            }
        }
    }
}

/** Smooth daily-spend trend with a soft gradient fill. */
@Composable
fun TrendLine(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Int = 120
) {
    val max = values.maxOrNull()?.toFloat() ?: 0f
    val progress by animateFloatAsState(
        targetValue = if (values.isNotEmpty()) 1f else 0f, animationSpec = tween(700), label = "trend"
    )
    val grid = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1).toFloat()
        val h = size.height * 0.9f

        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)

        fun pointAt(i: Int): Offset {
            val v = values[i].toFloat()
            val y = if (max > 0f) size.height - (v / max) * h * progress else size.height
            return Offset(stepX * i, y)
        }

        val line = Path()
        val fill = Path()
        line.moveTo(pointAt(0).x, pointAt(0).y)
        fill.moveTo(0f, size.height)
        fill.lineTo(pointAt(0).x, pointAt(0).y)

        // Catmull-Rom style smoothing via midpoint quadratics.
        for (i in 0 until values.size - 1) {
            val p0 = pointAt(i)
            val p1 = pointAt(i + 1)
            val midX = (p0.x + p1.x) / 2f
            line.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            fill.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
        fill.lineTo(size.width, size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.26f), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = 3.5f))
    }
}

/** Budget / share meter used on the dashboard. */
@Composable
fun ProgressMeter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thickness: Int = 10
) {
    val f by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f), animationSpec = tween(600), label = "meter"
    )
    Box(
        modifier.fillMaxWidth().height(thickness.dp).clip(CircleShape).bg(trackColor)
    ) {
        Box(Modifier.fillMaxWidth(f).height(thickness.dp).clip(CircleShape).bg(color))
    }
}

/** Deterministic fallback palette for buckets that carry no colour of their own. */
val ChartPalette = listOf(
    Color(0xFF12805C), Color(0xFF3D8FD1), Color(0xFFE0783C), Color(0xFF7C6BD6),
    Color(0xFFD64550), Color(0xFF2FA8A8), Color(0xFFD1A73D), Color(0xFFE06C9F),
    Color(0xFF6E8B3D), Color(0xFF8C6E4F), Color(0xFF5B8DEF), Color(0xFFB05CD6)
)

fun paletteFor(index: Int): Color = ChartPalette[index % ChartPalette.size]

@Composable
fun ChartEmpty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
