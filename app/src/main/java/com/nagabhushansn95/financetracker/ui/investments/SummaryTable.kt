package com.nagabhushansn95.financetracker.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nagabhushansn95.financetracker.data.investments.InvestmentSummary
import com.nagabhushansn95.financetracker.data.investments.SummaryLine
import com.nagabhushansn95.financetracker.ui.common.MoneyFormat

/** One figure of a summary line that can be a table column. */
enum class Metric(val label: String) {
    INVESTED("Invested"), CURRENT("Current"), HELD("Held P/L"),
    REALISED("Realised"), TOTAL("Total P/L"), XIRR("XIRR"),
    PCT_INVESTED("% inv"), PCT_CURRENT("% cur"), TARGET("Target");

    /** Text and sign (for colouring) of this metric for [line]. */
    fun cell(line: SummaryLine, s: InvestmentSummary): Pair<String, Double?> = when (this) {
        INVESTED -> short(line.invested) to null
        CURRENT -> short(line.current) to null
        HELD -> short(line.held) to line.held
        REALISED -> short(line.realised) to line.realised
        TOTAL -> short(line.total) to line.total
        XIRR -> pct(line.xirr) to null
        PCT_INVESTED -> pct(s.investedShare(line)) to null
        PCT_CURRENT -> pct(s.currentShare(line)) to null
        TARGET -> {
            val off = line.offTarget(s.total.current)
            (line.targetPct?.let { pct(it) + (if (off) " !" else "") }
                ?: "—") to (if (off) -1.0 else null)
        }
    }
}

/**
 * Which columns the summary tables show: three fixed sets, or the user's own pick of one to four metrics (kept in
 * preferences). The rest of a line sits in its expanded detail.
 */
enum class MetricView(val label: String, val metrics: List<Metric>) {
    VALUE("Value", listOf(Metric.INVESTED, Metric.CURRENT, Metric.HELD)),
    RETURNS("Returns", listOf(Metric.REALISED, Metric.TOTAL, Metric.XIRR)),
    ALLOCATION("Allocation", listOf(Metric.PCT_INVESTED, Metric.PCT_CURRENT, Metric.TARGET)),
    CUSTOM("Custom", emptyList());

    /** The columns shown: [custom] for [CUSTOM] (the Value set until something is picked). */
    fun columns(custom: List<Metric>): List<Metric> =
        if (this == CUSTOM) custom.ifEmpty { VALUE.metrics } else metrics

    companion object {
        const val MAX_CUSTOM = 4
    }
}

fun pct(value: Double?): String =
    value?.let { "%.1f%%".format(if (kotlin.math.abs(it) < 0.00005) 0.0 else it * 100) } ?: "—"
private fun short(value: Double) = MoneyFormat.compact(value)

/**
 * The column-set chips. Tapping Custom when it is already selected, or when nothing has been picked yet, opens the
 * picker.
 */
@Composable
fun MetricViewChips(
    view: MetricView,
    custom: List<Metric>,
    onChange: (MetricView) -> Unit,
    onCustomChange: (List<Metric>) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf(false) }
    // Four chips do not fit a narrow phone side by side, so the row scrolls.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricView.entries.forEach { v ->
            FilterChip(
                selected = view == v,
                onClick = {
                    if (v == MetricView.CUSTOM && (view == v || custom.isEmpty())) picking = true
                    onChange(v)
                },
                label = { Text(v.label) })
        }
    }
    if (picking) MetricPickerDialog(
        selected = custom,
        onDismiss = { picking = false },
        onConfirm = { picking = false; onCustomChange(it) },
    )
}

/** Pick one to four of the nine metrics; they show in the table in this fixed order. */
@Composable
private fun MetricPickerDialog(
    selected: List<Metric>,
    onDismiss: () -> Unit,
    onConfirm: (List<Metric>) -> Unit
) {
    var picked by remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom columns") },
        text = {
            Column {
                Text(
                    "Choose one to ${MetricView.MAX_CUSTOM} columns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Metric.entries.forEach { m ->
                    val on = m in picked
                    val full = !on && picked.size >= MetricView.MAX_CUSTOM
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !full) {
                                picked = if (on) picked - m else picked + m
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, enabled = !full)
                        Text(
                            m.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (full) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(Metric.entries.filter { it in picked }) },
            ) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The summary as a narrow table: a name column and up to four metric [columns] that fit a phone. Tap a row to expand
 * it: every metric, the notes, and the sub-lines underneath. With the Target column the allocation bars show.
 */
@Composable
fun SummaryTable(
    lines: List<SummaryLine>,
    summary: InvestmentSummary,
    columns: List<Metric>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    footer: SummaryLine? = null,
) {
    Column {
        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            Box(modifier = Modifier.weight(1.4f))
            columns.forEach {
                Text(
                    it.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider()
        lines.forEach { line ->
            LineBlock(
                line,
                summary,
                columns,
                key = line.name,
                indent = 0.dp,
                bold = true,
                expanded = expanded,
                onToggle = onToggle
            )
        }
        if (footer != null) {
            HorizontalDivider()
            LineRow(
                footer,
                summary,
                columns,
                bold = true,
                indent = 0.dp,
                marker = null,
                onClick = null,
                bar = false
            )
        }
    }
}

@Composable
private fun LineBlock(
    line: SummaryLine,
    s: InvestmentSummary,
    columns: List<Metric>,
    key: String,
    indent: Dp,
    bold: Boolean,
    expanded: Set<String>,
    onToggle: (String) -> Unit
) {
    val open = key in expanded
    LineRow(
        line,
        s,
        columns,
        bold = bold,
        indent = indent,
        marker = if (open) "▾" else "▸",
        onClick = { onToggle(key) })
    if (open) {
        LineDetails(line, s, indent = indent + 12.dp)
        line.children.forEach { child ->
            LineBlock(
                child,
                s,
                columns,
                key = "$key/${child.name}",
                indent = indent + 16.dp,
                bold = false,
                expanded = expanded,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun LineRow(
    line: SummaryLine,
    s: InvestmentSummary,
    columns: List<Metric>,
    bold: Boolean,
    indent: Dp,
    marker: String?,
    onClick: (() -> Unit)?,
    bar: Boolean = true
) {
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (marker?.let { "$it " } ?: "") + line.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = weight,
                modifier = Modifier
                    .weight(1.4f)
                    .padding(start = indent),
                maxLines = 2,
            )
            columns.forEach { m ->
                val (text, signed) = m.cell(line, s)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = weight,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    color = signedColour(signed),
                )
            }
        }
        if (bar && Metric.TARGET in columns && line.targetPct != null) {
            AllocationBar(
                share = s.currentShare(line),
                target = line.targetPct,
                tolerance = line.tolerancePct,
                modifier = Modifier.padding(start = indent, top = 3.dp)
            )
        }
    }
}

@Composable
private fun signedColour(signed: Double?) = when {
    signed == null -> MaterialTheme.colorScheme.onSurface
    signed < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

/**
 * Current share as a solid bar from the left, the target as a dark tick, and the tolerance as an outlined box around
 * the tick (a faint fill, so it reads as a range and never as a second bar). Scaled to the larger of the share and the
 * top of the band. Without a tolerance there is no box, only the tick.
 */
@Composable
fun AllocationBar(
    share: Double,
    target: Double,
    tolerance: Double?,
    modifier: Modifier = Modifier
) {
    val scale = maxOf(share, target + (tolerance ?: 0.0), 0.01) * 1.1
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val band = tolerance?.takeIf { it > 0 }?.let {
        ((target - it) / scale).coerceIn(0.0, 1.0)
            .toFloat() to ((target + it) / scale).coerceIn(0.0, 1.0).toFloat()
    }

    @Composable
    fun Span(from: Float, to: Float, content: @Composable () -> Unit) = Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (from > 0f) Box(modifier = Modifier.weight(from)) {}
        Box(
            modifier = Modifier
                .weight((to - from).coerceAtLeast(0.001f))
                .fillMaxHeight()
        ) { content() }
        if (to < 1f) Box(modifier = Modifier.weight(1f - to)) {}
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // The box's fill sits under the bar; its outline goes over the bar, so the range stays visible across it.
        band?.let { (from, to) ->
            Span(from, to) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(primary.copy(alpha = 0.15f))
                ) {}
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth((share / scale).coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(primary)
        ) {}
        band?.let { (from, to) ->
            Span(from, to) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, ink.copy(alpha = 0.6f))
                ) {}
            }
        }
        val t = (target / scale).coerceIn(0.0, 1.0).toFloat()
        Row(modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()) {
            if (t > 0f) Box(modifier = Modifier.weight(t)) {}
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(ink)
            ) {}
            if (t < 1f) Box(modifier = Modifier.weight(1f - t)) {}
        }
    }
}

/** Everything about a line, two metrics per row, with full amounts. */
@Composable
fun LineDetails(line: SummaryLine, s: InvestmentSummary, indent: Dp) {
    val pairs: List<Triple<String, String, Double?>> = listOf(
        Triple("Invested", MoneyFormat.amount(line.invested), null),
        Triple("Current", MoneyFormat.amount(line.current), null),
        Triple("Held P/L", MoneyFormat.amount(line.held), line.held),
        Triple("Realised", MoneyFormat.amount(line.realised), line.realised),
        Triple("Total P/L", MoneyFormat.amount(line.total), line.total),
        Triple("Income", MoneyFormat.amount(line.income), null),
        Triple("XIRR", pct(line.xirr), null),
        Triple("Risk", line.riskLevel ?: "—", null),
        Triple("% of invested", pct(s.investedShare(line)), null),
        Triple("% of current", pct(s.currentShare(line)), null),
        Triple(
            "Target",
            line.targetPct?.let { pct(it) + (line.tolerancePct?.let { t -> " ± " + pct(t) } ?: "") }
                ?: "—",
            if (line.offTarget(s.total.current)) -1.0 else null),
        Triple(
            "Drift",
            line.drift(s.total.current)?.let { (if (it >= 0) "+" else "") + pct(it) } ?: "—",
            line.drift(s.total.current)
                ?.let { if (line.offTarget(s.total.current)) -1.0 else 1.0 }),
        Triple("Goal share", line.goalPct?.let(::pct) ?: "—", null),
        Triple("Wealth share", line.wealthPct?.let(::pct) ?: "—", null),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pairs.chunked(2).forEach { pair ->
            Row {
                pair.forEach { (label, value, signed) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = signedColour(signed)
                        )
                    }
                }
            }
        }
        line.notes?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
