package com.smarttreasury.scheduler

import com.smarttreasury.service.BatchingEngine
import com.smarttreasury.service.FxRateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled tasks for FX rate polling and automatic batch optimization.
 * Uses Kotlin coroutines for async execution.
 */
@Component
class FxRateScheduler(
    private val fxRateService: FxRateService,
    private val batchingEngine: BatchingEngine
) {
    private val logger = LoggerFactory.getLogger(FxRateScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Every 1 hour: fetch latest FX rates from Frankfurter API,
     * store in Redis cache + persist snapshot to PostgreSQL.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun pollFxRates() {
        scope.launch {
            try {
                logger.info("Scheduled FX rate poll starting...")
                fxRateService.fetchAndPersistRates()
                logger.info("Scheduled FX rate poll completed.")
            } catch (e: Exception) {
                logger.error("Scheduled FX rate poll failed", e)
            }
        }
    }

    /**
     * Every day at 8am UTC: auto-run the batching engine on all PENDING
     * invoices and store PROPOSED batches.
     */
    @Scheduled(cron = "0 0 8 * * *")
    fun dailyBatchOptimization() {
        scope.launch {
            try {
                logger.info("Scheduled daily batch optimization starting...")
                val result = batchingEngine.optimize()
                logger.info("Daily optimization complete: ${result.batches.size} batches, saving: \$${result.totalEstimatedSaving}")
            } catch (e: Exception) {
                logger.error("Scheduled batch optimization failed", e)
            }
        }
    }
}
