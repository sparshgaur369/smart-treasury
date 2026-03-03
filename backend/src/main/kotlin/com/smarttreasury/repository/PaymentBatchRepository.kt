package com.smarttreasury.repository

import com.smarttreasury.model.BatchStatus
import com.smarttreasury.model.PaymentBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PaymentBatchRepository : JpaRepository<PaymentBatch, UUID> {
    fun findByStatus(status: BatchStatus): List<PaymentBatch>
    fun findAllByOrderByCreatedAtDesc(): List<PaymentBatch>
}
