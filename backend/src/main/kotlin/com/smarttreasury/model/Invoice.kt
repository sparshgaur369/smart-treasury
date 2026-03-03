package com.smarttreasury.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "invoices")
data class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "vendor_id", nullable = false)
    val vendorId: UUID = UUID.randomUUID(),

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, length = 3)
    val currency: String = "",

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: InvoiceStatus = InvoiceStatus.PENDING,

    val description: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
    var vendor: Vendor? = null
}
