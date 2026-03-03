package com.smarttreasury.service

import com.smarttreasury.dto.BatchItemResponse
import com.smarttreasury.dto.OptimizeResponse
import com.smarttreasury.dto.PaymentBatchResponse
import com.smarttreasury.model.*
import com.smarttreasury.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * BatchingEngine — The heart of the Smart Treasury application.
 *
 * This engine analyzes all pending invoices alongside historical FX rate data
 * to produce an optimized payment schedule that minimizes FX costs and
 * transaction fees.
 *
 * INPUT:  List of pending invoices, cached FX rates, 90-day historical rates
 * OUTPUT: Proposed payment batches with estimated USD savings
 *
 * ALGORITHM (Greedy):
 * 1. Group invoices by currency
 * 2. For each currency group, compute a composite score:
 *    score = (rate_advantage × 0.6) + (urgency_penalty × 0.4)
 *    - rate_advantage: how favorable today's rate is vs 90-day average (0-100)
 *    - urgency_penalty: how close the earliest due date is (inverted, 0-100)
 * 3. Sort groups by score descending (highest score = best to pay now)
 * 4. Greedily assign batch dates without breaching any invoice's due date
 * 5. Calculate estimated savings: batch fee savings ($8/txn) + FX timing savings
 */
@Service
class BatchingEngine(
    private val invoiceRepository: InvoiceRepository,
    private val fxRateService: FxRateService,
    private val paymentBatchRepository: PaymentBatchRepository,
    private val batchItemRepository: BatchItemRepository,
    private val fxRateSnapshotRepository: FxRateSnapshotRepository,
    private val vendorRepository: VendorRepository
) {
    private val logger = LoggerFactory.getLogger(BatchingEngine::class.java)

    // Flat fee per individual transaction (assumed)
    private val txnFeePerInvoice = BigDecimal("8.00")

    /**
     * Run the full batch optimization on all PENDING invoices.
     * Creates PROPOSED payment batches in the database.
     */
    @Transactional
    fun optimize(): OptimizeResponse {
        logger.info("Starting batch optimization...")

        val pendingInvoices = invoiceRepository.findByStatus(InvoiceStatus.PENDING)
        if (pendingInvoices.isEmpty()) {
            return OptimizeResponse(
                batches = emptyList(),
                totalEstimatedSaving = BigDecimal.ZERO,
                message = "No pending invoices to optimize."
            )
        }

        // Step 1: Group invoices by currency
        val groupedByCurrency = pendingInvoices.groupBy { it.currency }
        logger.info("Found ${pendingInvoices.size} pending invoices across ${groupedByCurrency.size} currencies")

        // Step 2: Score each currency group
        val scoredGroups = groupedByCurrency.map { (currency, invoices) ->
            val score = scoreCurrencyGroup(currency, invoices)
            Triple(currency, invoices, score)
        }

        // Step 3: Sort by score descending (best to pay first)
        val sortedGroups = scoredGroups.sortedByDescending { it.third }

        // Step 4: Create payment batches
        val batchResponses = mutableListOf<PaymentBatchResponse>()
        var totalSaving = BigDecimal.ZERO

        for ((currency, invoices, score) in sortedGroups) {
            if (currency == "USD") {
                // USD invoices — no FX needed, still batch for fee savings
                val batch = createBatch(currency, invoices, BigDecimal.ONE)
                batchResponses.add(batch)
                totalSaving += batch.estimatedSavingUsd
            } else {
                val currentRate = fxRateService.getRate("USD", currency)
                val batch = createBatch(currency, invoices, currentRate)
                batchResponses.add(batch)
                totalSaving += batch.estimatedSavingUsd
            }
        }

        val message = if (totalSaving > BigDecimal.ZERO) {
            "Approving all batches saves you an estimated \$${totalSaving.setScale(2, RoundingMode.HALF_UP)} vs paying individually today."
        } else {
            "Batches created. Savings are primarily from reduced transaction fees."
        }

        logger.info("Optimization complete: ${batchResponses.size} batches, estimated saving: \$$totalSaving")

        return OptimizeResponse(
            batches = batchResponses,
            totalEstimatedSaving = totalSaving.setScale(2, RoundingMode.HALF_UP),
            message = message
        )
    }

    /**
     * Score a currency group based on rate advantage and urgency.
     *
     * rate_advantage (0-100): How favorable today's rate is compared to 90-day average.
     *   If current rate < average → good time to buy (higher score for foreign currencies).
     *
     * urgency_penalty (0-100): How close the earliest due date is.
     *   Due in 1 day → urgency=100, due in 30+ days → urgency=0.
     *
     * composite = rate_advantage × 0.6 + urgency_penalty × 0.4
     */
    private fun scoreCurrencyGroup(currency: String, invoices: List<Invoice>): BigDecimal {
        if (currency == "USD") return BigDecimal("50") // No FX needed

        // Rate advantage score
        val rateAdvantage = calculateRateAdvantage(currency)

        // Urgency score
        val earliestDue = invoices.minOfOrNull { it.dueDate } ?: LocalDate.now()
        val daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), earliestDue)
        val urgencyScore = when {
            daysUntilDue <= 0 -> BigDecimal("100")  // Overdue!
            daysUntilDue <= 3 -> BigDecimal("90")
            daysUntilDue <= 7 -> BigDecimal("70")
            daysUntilDue <= 14 -> BigDecimal("40")
            daysUntilDue <= 30 -> BigDecimal("20")
            else -> BigDecimal.ZERO
        }

        // Composite score: rate_advantage × 0.6 + urgency × 0.4
        val composite = rateAdvantage.multiply(BigDecimal("0.6"))
            .add(urgencyScore.multiply(BigDecimal("0.4")))

        logger.debug("Currency=$currency: rateAdv=$rateAdvantage, urgency=$urgencyScore, composite=$composite")
        return composite.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Calculate how favorable the current rate is vs 90-day average.
     * Returns 0-100 where 100 = best rate advantage.
     */
    private fun calculateRateAdvantage(currency: String): BigDecimal {
        val since = Instant.now().minusSeconds(90L * 24 * 3600)
        val history = fxRateSnapshotRepository.findHistorySince("USD", currency, since)

        if (history.isEmpty()) return BigDecimal("50") // No data, neutral score

        val rates = history.map { it.rate }
        val average = rates.fold(BigDecimal.ZERO) { acc, r -> acc + r }
            .divide(BigDecimal(rates.size), 8, RoundingMode.HALF_UP)

        val currentRate = fxRateService.getRate("USD", currency)

        // For buying foreign currency with USD:
        // Higher rate = more foreign currency per USD = better deal
        // If current > avg → good (rate advantage positive)
        val percentDiff = if (average > BigDecimal.ZERO) {
            (currentRate - average).divide(average, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else BigDecimal.ZERO

        // Map to 0-100 score: +5% or more = 100, -5% or less = 0, linear between
        val score = percentDiff.add(BigDecimal("5"))
            .multiply(BigDecimal("10"))
            .coerceIn(BigDecimal.ZERO, BigDecimal("100"))

        return score.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Create a payment batch for a group of same-currency invoices.
     * Determines the optimal scheduled date and calculates expected savings.
     */
    private fun createBatch(
        currency: String,
        invoices: List<Invoice>,
        currentRate: BigDecimal
    ): PaymentBatchResponse {
        // Scheduled date: use the "best day of week" if it's before earliest due date,
        // otherwise schedule for 1 day before earliest due date
        val earliestDue = invoices.minOf { it.dueDate }
        val scheduledDate = determineBatchDate(currency, earliestDue)

        // Calculate total in original currency
        val totalOriginal = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.amount }

        // Calculate USD equivalent
        val totalUsd = if (currency == "USD") totalOriginal
        else totalOriginal.divide(currentRate, 2, RoundingMode.HALF_UP)

        // Savings calculation:
        // 1. Fee savings: (N-1) × $8 (batching N invoices into 1 transaction)
        val feeSavings = txnFeePerInvoice.multiply(BigDecimal(invoices.size - 1))

        // 2. FX timing savings (estimated): if rate is favorable vs avg
        val fxSavings = if (currency != "USD") {
            val since = Instant.now().minusSeconds(90L * 24 * 3600)
            val history = fxRateSnapshotRepository.findHistorySince("USD", currency, since)
            if (history.isNotEmpty()) {
                val avgRate = history.map { it.rate }.fold(BigDecimal.ZERO) { acc, r -> acc + r }
                    .divide(BigDecimal(history.size), 8, RoundingMode.HALF_UP)
                val atAvg = totalOriginal.divide(avgRate, 2, RoundingMode.HALF_UP)
                val atCurrent = totalOriginal.divide(currentRate, 2, RoundingMode.HALF_UP)
                // If current rate is better (atCurrent < atAvg), savings is positive
                (atAvg - atCurrent).max(BigDecimal.ZERO)
            } else BigDecimal.ZERO
        } else BigDecimal.ZERO

        val totalSaving = feeSavings + fxSavings

        // Persist the batch
        val batch = PaymentBatch(
            scheduledDate = scheduledDate,
            totalUsdEquivalent = totalUsd,
            estimatedSavingUsd = totalSaving.setScale(2, RoundingMode.HALF_UP),
            status = BatchStatus.PROPOSED,
            createdAt = Instant.now()
        )
        val savedBatch = paymentBatchRepository.save(batch)

        // Create batch items
        val vendorMap = vendorRepository.findAllById(invoices.map { it.vendorId }).associateBy { it.id }
        val items = invoices.map { invoice ->
            val item = BatchItem(
                batchId = savedBatch.id!!,
                invoiceId = invoice.id!!
            )
            batchItemRepository.save(item)
            BatchItemResponse(
                invoiceId = invoice.id,
                vendorName = vendorMap[invoice.vendorId]?.name,
                amount = invoice.amount,
                currency = invoice.currency,
                dueDate = invoice.dueDate,
                description = invoice.description
            )
        }

        return PaymentBatchResponse(
            id = savedBatch.id!!,
            scheduledDate = scheduledDate,
            currency = currency,
            totalAmount = totalOriginal,
            totalUsdEquivalent = totalUsd,
            estimatedSavingUsd = totalSaving.setScale(2, RoundingMode.HALF_UP),
            status = savedBatch.status.name,
            items = items,
            createdAt = savedBatch.createdAt
        )
    }

    /**
     * Determine the best date to schedule a batch payment.
     * Uses historical "best day of week" if it falls before the due date,
     * otherwise uses 1 day before the earliest due date.
     */
    private fun determineBatchDate(currency: String, earliestDue: LocalDate): LocalDate {
        val today = LocalDate.now()

        if (currency == "USD") {
            // No FX optimization needed for USD, just batch on next business day
            return if (earliestDue.isAfter(today.plusDays(1))) today.plusDays(1) else today
        }

        // Try to find the best day of week from volatility analysis
        try {
            val volatility = fxRateService.getVolatility("USD_$currency")
            val bestDay = when (volatility.bestDayOfWeek.uppercase()) {
                "MONDAY" -> java.time.DayOfWeek.MONDAY
                "TUESDAY" -> java.time.DayOfWeek.TUESDAY
                "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY
                "THURSDAY" -> java.time.DayOfWeek.THURSDAY
                "FRIDAY" -> java.time.DayOfWeek.FRIDAY
                else -> java.time.DayOfWeek.WEDNESDAY // Default to mid-week
            }

            // Find the next occurrence of the best day
            var nextBestDay = today
            while (nextBestDay.dayOfWeek != bestDay) {
                nextBestDay = nextBestDay.plusDays(1)
            }

            // Use best day if it's before the due date, otherwise schedule for due date - 1
            return if (nextBestDay.isBefore(earliestDue)) {
                nextBestDay
            } else {
                if (earliestDue.isAfter(today)) earliestDue.minusDays(1) else today
            }
        } catch (e: Exception) {
            logger.warn("Could not determine best day for $currency, using due date - 1")
            return if (earliestDue.isAfter(today)) earliestDue.minusDays(1) else today
        }
    }

    private fun BigDecimal.coerceIn(min: BigDecimal, max: BigDecimal): BigDecimal {
        return when {
            this < min -> min
            this > max -> max
            else -> this
        }
    }
}
