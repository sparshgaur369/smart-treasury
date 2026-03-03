package com.smarttreasury.repository

import com.smarttreasury.model.FxRateSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface FxRateSnapshotRepository : JpaRepository<FxRateSnapshot, UUID> {

    fun findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(
        baseCurrency: String,
        targetCurrency: String
    ): FxRateSnapshot?

    @Query("""
        SELECT f FROM FxRateSnapshot f 
        WHERE f.baseCurrency = :base AND f.targetCurrency = :target 
        AND f.fetchedAt >= :since 
        ORDER BY f.fetchedAt ASC
    """)
    fun findHistorySince(base: String, target: String, since: Instant): List<FxRateSnapshot>

    fun existsByBaseCurrencyAndTargetCurrency(baseCurrency: String, targetCurrency: String): Boolean
}
