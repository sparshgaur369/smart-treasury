package com.smarttreasury.service

import com.smarttreasury.dto.CashFlowPoint
import com.smarttreasury.dto.CurrencyExposure
import com.smarttreasury.dto.DashboardSummary
import com.smarttreasury.model.InvoiceStatus
import com.smarttreasury.repository.InvoiceRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class DashboardService(
    private val invoiceRepository: InvoiceRepository,
    private val fxRateService: FxRateService
) {

    fun getSummary(): DashboardSummary {
        val pendingInvoices = invoiceRepository.findByStatus(InvoiceStatus.PENDING)
        val today = LocalDate.now()

        // Currency exposures
        val grouped = pendingInvoices.groupBy { it.currency }
        val exposures = grouped.map { (currency, invoices) ->
            val totalAmount = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.amount }
            val rate = if (currency == "USD") BigDecimal.ONE
            else fxRateService.getRate("USD", currency)
            val usdEquiv = if (rate > BigDecimal.ZERO)
                totalAmount.divide(rate, 2, RoundingMode.HALF_UP)
            else totalAmount

            CurrencyExposure(
                currency = currency,
                totalAmount = totalAmount,
                usdEquivalent = usdEquiv,
                invoiceCount = invoices.size
            )
        }

        val totalLiability = exposures.fold(BigDecimal.ZERO) { acc, e -> acc + e.usdEquivalent }

        // Due date counts
        val dueThisWeek = pendingInvoices.count { it.dueDate <= today.plusDays(7) }
        val due14Days = pendingInvoices.count { it.dueDate <= today.plusDays(14) }
        val due30Days = pendingInvoices.count { it.dueDate <= today.plusDays(30) }

        // Estimated savings (rough: $8 per invoice if batched - minimum 1 txn per currency)
        val savingsEstimate = BigDecimal("8")
            .multiply(BigDecimal(maxOf(0, pendingInvoices.size - grouped.size)))

        // Cash flow timeline (next 30 days)
        val cashFlow = mutableListOf<CashFlowPoint>()
        var cumulative = BigDecimal.ZERO
        for (dayOffset in 0..30) {
            val date = today.plusDays(dayOffset.toLong())
            val dayInvoices = pendingInvoices.filter { it.dueDate == date }
            val dayAmount = dayInvoices.fold(BigDecimal.ZERO) { acc, inv ->
                val rate = if (inv.currency == "USD") BigDecimal.ONE
                else fxRateService.getRate("USD", inv.currency)
                if (rate > BigDecimal.ZERO)
                    acc + inv.amount.divide(rate, 2, RoundingMode.HALF_UP)
                else acc + inv.amount
            }
            cumulative += dayAmount
            cashFlow.add(
                CashFlowPoint(
                    date = date.toString(),
                    amount = dayAmount,
                    cumulativeAmount = cumulative
                )
            )
        }

        // Alerts
        val alerts = mutableListOf<String>()
        val currencies = listOf("EUR", "INR", "GBP", "BRL")
        for (currency in currencies) {
            try {
                val vol = fxRateService.getVolatility("USD_$currency")
                if (vol.percentFromAverage < BigDecimal("-1.5")) {
                    alerts.add("$currency/USD is ${vol.percentFromAverage.abs()}% below 90-day average — good window to pay $currency invoices")
                }
            } catch (_: Exception) {}
        }

        return DashboardSummary(
            totalPendingLiabilityUsd = totalLiability,
            invoicesDueThisWeek = dueThisWeek,
            invoicesDue14Days = due14Days,
            invoicesDue30Days = due30Days,
            estimatedSavingsAvailable = savingsEstimate,
            currencyExposures = exposures,
            cashFlowTimeline = cashFlow,
            alerts = alerts
        )
    }
}
