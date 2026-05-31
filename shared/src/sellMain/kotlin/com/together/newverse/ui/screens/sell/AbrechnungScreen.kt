package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AbrechnungScreen(
    viewModel: AbrechnungViewModel = koinViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val pickupSummary by viewModel.pickupSummary.collectAsState()
    val periodSummary by viewModel.periodSummary.collectAsState()

    AbrechnungContent(
        selectedTab = selectedTab,
        selectedPeriod = selectedPeriod,
        pickupSummary = pickupSummary,
        periodSummary = periodSummary,
        onTabSelected = viewModel::selectTab,
        onPeriodSelected = viewModel::setPeriod
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbrechnungContent(
    selectedTab: AbrechnungTab,
    selectedPeriod: PeriodFilter,
    pickupSummary: AsyncState<PickupSummary>,
    periodSummary: AsyncState<PeriodSummary>,
    onTabSelected: (AbrechnungTab) -> Unit,
    onPeriodSelected: (PeriodFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == AbrechnungTab.PICKUP,
                onClick = { onTabSelected(AbrechnungTab.PICKUP) },
                text = { Text(stringResource(Res.string.abrechnung_tab_pickup)) }
            )
            Tab(
                selected = selectedTab == AbrechnungTab.PERIOD,
                onClick = { onTabSelected(AbrechnungTab.PERIOD) },
                text = { Text(stringResource(Res.string.abrechnung_tab_period)) }
            )
        }

        when (selectedTab) {
            AbrechnungTab.PICKUP -> PickupView(pickupSummary)
            AbrechnungTab.PERIOD -> PeriodView(
                periodSummary = periodSummary,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected
            )
        }
    }
}

// ─── View 1: Nächste Abholung ──────────────────────────────────────────────

@Composable
private fun PickupView(state: AsyncState<PickupSummary>) {
    when (state) {
        AsyncState.Initial, AsyncState.Loading -> LoadingBox()
        is AsyncState.Error -> ErrorBox(state.message)
        is AsyncState.Success -> {
            val summary = state.data
            if (summary.orderCount == 0) {
                EmptyBox(stringResource(Res.string.abrechnung_no_orders))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }

                    item { PickupHeaderCard(summary) }

                    item { FinancialSummaryCard(summary.financials) }

                    item {
                        Text(
                            text = stringResource(Res.string.abrechnung_article_list),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(summary.aggregatedItems) { item ->
                        AggregatedArticleRow(item)
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickupHeaderCard(summary: PickupSummary) {
    val tz = TimeZone.currentSystemDefault()
    val dateStr = OrderDateUtils.formatDisplayDate(
        kotlin.time.Instant.fromEpochMilliseconds(summary.pickupDateMs)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.abrechnung_pickup_date, dateStr),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatChip(
                    label = stringResource(Res.string.abrechnung_orders_count, summary.orderCount),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatChip(
                    label = stringResource(Res.string.abrechnung_customers_count, summary.customerCount),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AggregatedArticleRow(item: AggregatedItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatQuantity(item.totalQuantity, item.unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.totalGross.formatPrice()} €",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${item.pricePerUnit.formatPrice()} €/${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── View 2: Zeitraum ──────────────────────────────────────────────────────

@Composable
private fun PeriodView(
    periodSummary: AsyncState<PeriodSummary>,
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PeriodFilterRow(selectedPeriod, onPeriodSelected)

        when (periodSummary) {
            AsyncState.Initial, AsyncState.Loading -> LoadingBox()
            is AsyncState.Error -> ErrorBox(periodSummary.message)
            is AsyncState.Success -> {
                val summary = periodSummary.data
                if (summary.orderCount == 0) {
                    EmptyBox(stringResource(Res.string.abrechnung_no_completed_orders))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                StatChip(stringResource(Res.string.abrechnung_orders_count, summary.orderCount))
                                StatChip(stringResource(Res.string.abrechnung_customers_count, summary.customerCount))
                            }
                        }

                        item { FinancialSummaryCard(summary.financials) }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == PeriodFilter.WEEK,
            onClick = { onSelect(PeriodFilter.WEEK) },
            label = { Text(stringResource(Res.string.abrechnung_period_week)) }
        )
        FilterChip(
            selected = selected == PeriodFilter.MONTH,
            onClick = { onSelect(PeriodFilter.MONTH) },
            label = { Text(stringResource(Res.string.abrechnung_period_month)) }
        )
        FilterChip(
            selected = selected == PeriodFilter.ALL,
            onClick = { onSelect(PeriodFilter.ALL) },
            label = { Text(stringResource(Res.string.abrechnung_period_all)) }
        )
    }
}

// ─── Shared components ─────────────────────────────────────────────────────

@Composable
private fun FinancialSummaryCard(financials: OrderFinancials) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.abrechnung_financial_summary),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FinancialRow(
                label = stringResource(Res.string.abrechnung_gross_total),
                value = financials.grossTotal,
                bold = true
            )

            if (financials.vatAmount7 > 0.001) {
                FinancialRow(
                    label = stringResource(Res.string.abrechnung_vat_7),
                    value = financials.vatAmount7,
                    indent = true
                )
            }
            if (financials.vatAmount19 > 0.001) {
                FinancialRow(
                    label = stringResource(Res.string.abrechnung_vat_19),
                    value = financials.vatAmount19,
                    indent = true
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            FinancialRow(
                label = stringResource(Res.string.abrechnung_net_total),
                value = financials.netTotal,
                bold = true
            )

            if (financials.hasAcquireCost) {
                Spacer(Modifier.height(4.dp))
                FinancialRow(
                    label = stringResource(Res.string.abrechnung_acquire_cost),
                    value = financials.acquireCost
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FinancialRow(
                    label = stringResource(Res.string.abrechnung_gross_profit),
                    value = financials.grossProfit,
                    bold = true,
                    highlight = true
                )
            }
        }
    }
}

@Composable
private fun FinancialRow(
    label: String,
    value: Double,
    bold: Boolean = false,
    indent: Boolean = false,
    highlight: Boolean = false
) {
    val textStyle = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    val fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    val color = when {
        highlight && value > 0 -> MaterialTheme.colorScheme.primary
        highlight && value < 0 -> MaterialTheme.colorScheme.error
        indent -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 12.dp else 0.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = textStyle, fontWeight = fontWeight, color = color, modifier = Modifier.weight(1f))
        Text(
            text = "${value.formatPrice()} €",
            style = textStyle,
            fontWeight = fontWeight,
            color = color,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatChip(label: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.abrechnung_loading), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun EmptyBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

private fun formatQuantity(quantity: Double, unit: String): String {
    val formatted = if (unit.lowercase() == "kg") {
        val rounded = (quantity * 1000).toLong() / 1000.0
        val whole = rounded.toLong()
        val frac = ((rounded - whole) * 1000).toLong()
        "$whole,${frac.toString().padStart(3, '0')}"
    } else {
        val isWholeNumber = quantity == kotlin.math.floor(quantity)
        if (isWholeNumber) quantity.toLong().toString()
        else {
            val rounded = (quantity * 100).toLong() / 100.0
            val whole = rounded.toLong()
            val frac = ((rounded - whole) * 100).toLong()
            "$whole,${frac.toString().padStart(2, '0')}"
        }
    }
    return "$formatted $unit"
}
