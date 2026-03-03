package com.smarttreasury.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "fx_rate_snapshots")
data class FxRateSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "base_currency", nullable = false, length = 3)
    val baseCurrency: String = "",

    @Column(name = "target_currency", nullable = false, length = 3)
    val targetCurrency: String = "",

    @Column(nullable = false, precision = 18, scale = 8)
    val rate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "fetched_at")
    val fetchedAt: Instant = Instant.now()
)
