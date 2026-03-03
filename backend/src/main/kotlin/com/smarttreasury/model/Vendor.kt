package com.smarttreasury.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "vendors")
data class Vendor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String = "",

    @Column(nullable = false)
    val country: String = "",

    @Column(name = "preferred_currency", nullable = false, length = 3)
    val preferredCurrency: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_details", columnDefinition = "jsonb")
    val bankDetails: Map<String, Any> = emptyMap(),

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
