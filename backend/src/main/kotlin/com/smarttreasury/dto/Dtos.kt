package com.smarttreasury.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

// ===== Vendor DTOs =====

data class CreateVendorRequest(
    val name: String,
    val country: String,
    val preferredCurrency: String,
    val bankDetails: Map<String, Any> = emptyMap()
)

data class VendorResponse(
    val id: UUID,
    val name: String,
    val country: String,
    val preferredCurrency: String,
    val bankDetails: Map<String, Any>,
    val createdAt: Instant
)

// ===== Invoice DTOs =====

data class CreateInvoiceRequest(
    val vendorId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val description: String?
)

data class InvoiceResponse(
    val id: UUID,
    val vendorId: UUID,
    val vendorName: String?,
    val amount: BigDecimal,
    val currency: String,
    val usdEquivalent: BigDecimal?,
    val dueDate: LocalDate,
    val status: String,
    val description: String?,
    val createdAt: Instant
)

// ===== FX Rate DTOs =====

data class FxRateResponse(
    val base: String,
    val target: String,
    val rate: BigDecimal,
    val fetchedAt: Instant
)

data class FxHistoryPoint(
    val date: String,
    val rate: BigDecimal
)

data class FxHistoryResponse(
    val pair: String,
    val days: Int,
    val history: List<FxHistoryPoint>
)

data class FxVolatilityResponse(
    val pair: String,
    val volatilityScore: BigDecimal,
    val volatilityLevel: String, // LOW, MEDIUM, HIGH
    val bestDayOfWeek: String,
    val currentRate: BigDecimal,
    val ninetyDayAverage: BigDecimal,
    val percentFromAverage: BigDecimal
)

// ===== Batch DTOs =====

data class BatchItemResponse(
    val invoiceId: UUID,
    val vendorName: String?,
    val amount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val description: String?
)

data class PaymentBatchResponse(
    val id: UUID,
    val scheduledDate: LocalDate,
    val currency: String?,
    val totalAmount: BigDecimal?,
    val totalUsdEquivalent: BigDecimal,
    val estimatedSavingUsd: BigDecimal,
    val status: String,
    val items: List<BatchItemResponse>,
    val createdAt: Instant
)

data class OptimizeResponse(
    val batches: List<PaymentBatchResponse>,
    val totalEstimatedSaving: BigDecimal,
    val message: String
)

// ===== Dashboard DTOs =====

data class CurrencyExposure(
    val currency: String,
    val totalAmount: BigDecimal,
    val usdEquivalent: BigDecimal,
    val invoiceCount: Int
)

data class CashFlowPoint(
    val date: String,
    val amount: BigDecimal,
    val cumulativeAmount: BigDecimal
)

data class DashboardSummary(
    val totalPendingLiabilityUsd: BigDecimal,
    val invoicesDueThisWeek: Int,
    val invoicesDue14Days: Int,
    val invoicesDue30Days: Int,
    val estimatedSavingsAvailable: BigDecimal,
    val currencyExposures: List<CurrencyExposure>,
    val cashFlowTimeline: List<CashFlowPoint>,
    val alerts: List<String>
)
