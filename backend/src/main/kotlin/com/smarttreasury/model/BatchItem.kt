package com.smarttreasury.model

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "batch_items")
data class BatchItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "batch_id", nullable = false)
    val batchId: UUID = UUID.randomUUID(),

    @Column(name = "invoice_id", nullable = false)
    val invoiceId: UUID = UUID.randomUUID()
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    var batch: PaymentBatch? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", insertable = false, updatable = false)
    var invoice: Invoice? = null
}
