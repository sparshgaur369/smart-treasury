package com.smarttreasury.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "payment_batches")
data class PaymentBatch(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "scheduled_date", nullable = false)
    val scheduledDate: LocalDate = LocalDate.now(),

    @Column(name = "total_usd_equivalent", precision = 15, scale = 2)
    val totalUsdEquivalent: BigDecimal = BigDecimal.ZERO,

    @Column(name = "estimated_saving_usd", precision = 15, scale = 2)
    val estimatedSavingUsd: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BatchStatus = BatchStatus.PROPOSED,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "batch", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var items: MutableList<BatchItem> = mutableListOf()
}
