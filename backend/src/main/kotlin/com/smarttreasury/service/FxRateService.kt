package com.smarttreasury.service

import com.smarttreasury.client.FrankfurterClient
import com.smarttreasury.dto.FxHistoryPoint
import com.smarttreasury.dto.FxHistoryResponse
import com.smarttreasury.dto.FxRateResponse
import com.smarttreasury.dto.FxVolatilityResponse
import com.smarttreasury.model.FxRateSnapshot
import com.smarttreasury.repository.FxRateSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class FxRateService(
    private val frankfurterClient: FrankfurterClient,
    private val fxRateSnapshotRepository: FxRateSnapshotRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(FxRateService::class.java)
    private val supportedCurrencies = listOf("EUR", "INR", "GBP", "BRL")

    /**
     * Get latest FX rates. Checks Redis cache first, falls back to API.
     */
    fun getLatestRates(base: String, targets: List<String>): List<FxRateResponse> {
        val results = mutableListOf<FxRateResponse>()
        val needsFetch = mutableListOf<String>()

        // Check Redis cache first
        for (target in targets) {
            val cacheKey = "fx:latest:${base}_$target"
            try {
                val cached = redisTemplate.opsForValue().get(cacheKey)
                if (cached != null && cached is Map<*, *>) {
                    results.add(
                        FxRateResponse(
                            base = base,
                            target = target,
                            rate = BigDecimal(cached["rate"].toString()),
                            fetchedAt = Instant.parse(cached["fetchedAt"].toString())
                        )
                    )
                    continue
                }
            } catch (e: Exception) {
                logger.warn("Redis cache miss for $cacheKey: ${e.message}")
            }
            needsFetch.add(target)
        }

        // Fetch missing rates from API
        if (needsFetch.isNotEmpty()) {
            val freshRates = frankfurterClient.getLatestRates(base, needsFetch)
            val now = Instant.now()
            for ((target, rate) in freshRates) {
                val response = FxRateResponse(base = base, target = target, rate = rate, fetchedAt = now)
                results.add(response)

                // Cache in Redis for 1 hour
                try {
                    val cacheKey = "fx:latest:${base}_$target"
                    redisTemplate.opsForValue().set(
                        cacheKey,
                        mapOf("rate" to rate.toString(), "fetchedAt" to now.toString()),
                        1, TimeUnit.HOURS
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to cache rate in Redis: ${e.message}")
                }
            }
        }

        return results
    }

    /**
     * Get rate for a specific currency pair — used internally.
     */
    fun getRate(base: String, target: String): BigDecimal {
        if (base == target) return BigDecimal.ONE
        val rates = getLatestRates(base, listOf(target))
        return rates.firstOrNull()?.rate ?: getLatestFromDb(base, target)
    }

    private fun getLatestFromDb(base: String, target: String): BigDecimal {
        return fxRateSnapshotRepository
            .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(base, target)
            ?.rate ?: BigDecimal.ONE
    }

    /**
     * Get historical rate data for the last N days.
     */
    fun getHistory(pair: String, days: Int): FxHistoryResponse {
        val (base, target) = pair.split("_")
        val since = Instant.now().minusSeconds(days.toLong() * 24 * 3600)
        val snapshots = fxRateSnapshotRepository.findHistorySince(base, target, since)

        val history = snapshots.map { snapshot ->
            FxHistoryPoint(
                date = snapshot.fetchedAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                rate = snapshot.rate
            )
        }

        return FxHistoryResponse(pair = pair, days = days, history = history)
    }

    /**
     * Calculate volatility score and best day of week for a currency pair.
     *
     * Volatility = standard deviation of daily % changes over 90 days.
     * Best day = day of week with historically lowest average rate
     *           (cheapest day to buy foreign currency with USD).
     */
    fun getVolatility(pair: String): FxVolatilityResponse {
        val (base, target) = pair.split("_")
        val since = Instant.now().minusSeconds(90L * 24 * 3600)
        val snapshots = fxRateSnapshotRepository.findHistorySince(base, target, since)

        if (snapshots.size < 2) {
            return FxVolatilityResponse(
                pair = pair,
                volatilityScore = BigDecimal.ZERO,
                volatilityLevel = "LOW",
                bestDayOfWeek = "Monday",
                currentRate = getRate(base, target),
                ninetyDayAverage = BigDecimal.ZERO,
                percentFromAverage = BigDecimal.ZERO
            )
        }

        // Calculate daily percentage changes
        val dailyChanges = mutableListOf<BigDecimal>()
        for (i in 1 until snapshots.size) {
            val prev = snapshots[i - 1].rate
            val curr = snapshots[i].rate
            if (prev > BigDecimal.ZERO) {
                val change = (curr - prev).divide(prev, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                dailyChanges.add(change)
            }
        }

        // Volatility = standard deviation of daily changes
        val mean = if (dailyChanges.isNotEmpty()) {
            dailyChanges.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                .divide(BigDecimal(dailyChanges.size), 8, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val variance = if (dailyChanges.isNotEmpty()) {
            dailyChanges.fold(BigDecimal.ZERO) { acc, v ->
                val diff = v - mean
                acc + diff.multiply(diff)
            }.divide(BigDecimal(dailyChanges.size), 8, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val volatilityScore = variance.sqrt(MathContext(8))

        val volatilityLevel = when {
            volatilityScore < BigDecimal("0.3") -> "LOW"
            volatilityScore < BigDecimal("0.7") -> "MEDIUM"
            else -> "HIGH"
        }

        // Best day of week = day with lowest average rate
        val dayRates = mutableMapOf<DayOfWeek, MutableList<BigDecimal>>()
        for (snapshot in snapshots) {
            val day = snapshot.fetchedAt.atZone(ZoneOffset.UTC).dayOfWeek
            dayRates.getOrPut(day) { mutableListOf() }.add(snapshot.rate)
        }

        val bestDay = dayRates.entries
            .map { (day, rates) ->
                day to rates.fold(BigDecimal.ZERO) { acc, r -> acc + r }
                    .divide(BigDecimal(rates.size), 8, RoundingMode.HALF_UP)
            }
            .minByOrNull { it.second }
            ?.first ?: DayOfWeek.MONDAY

        // 90-day average
        val allRates = snapshots.map { it.rate }
        val avg = allRates.fold(BigDecimal.ZERO) { acc, r -> acc + r }
            .divide(BigDecimal(allRates.size), 8, RoundingMode.HALF_UP)

        val currentRate = getRate(base, target)
        val percentFromAvg = if (avg > BigDecimal.ZERO) {
            (currentRate - avg).divide(avg, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
        } else BigDecimal.ZERO

        return FxVolatilityResponse(
            pair = pair,
            volatilityScore = volatilityScore.setScale(4, RoundingMode.HALF_UP),
            volatilityLevel = volatilityLevel,
            bestDayOfWeek = bestDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            currentRate = currentRate,
            ninetyDayAverage = avg.setScale(4, RoundingMode.HALF_UP),
            percentFromAverage = percentFromAvg.setScale(2, RoundingMode.HALF_UP)
        )
    }

    /**
     * Fetch and persist latest rates for all supported pairs - called by scheduler.
     */
    fun fetchAndPersistRates() {
        logger.info("Fetching and persisting FX rates...")
        val rates = frankfurterClient.getLatestRates("USD", supportedCurrencies)
        val now = Instant.now()

        for ((target, rate) in rates) {
            val snapshot = FxRateSnapshot(
                baseCurrency = "USD",
                targetCurrency = target,
                rate = rate,
                fetchedAt = now
            )
            fxRateSnapshotRepository.save(snapshot)

            // Update Redis cache
            try {
                val cacheKey = "fx:latest:USD_$target"
                redisTemplate.opsForValue().set(
                    cacheKey,
                    mapOf("rate" to rate.toString(), "fetchedAt" to now.toString()),
                    1, TimeUnit.HOURS
                )
            } catch (e: Exception) {
                logger.warn("Failed to update Redis cache: ${e.message}")
            }
        }
        logger.info("Persisted ${rates.size} FX rate snapshots")
    }
}
