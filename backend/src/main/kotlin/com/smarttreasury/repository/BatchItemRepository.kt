package com.smarttreasury.repository

import com.smarttreasury.model.BatchItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BatchItemRepository : JpaRepository<BatchItem, UUID> {
    fun findByBatchId(batchId: UUID): List<BatchItem>
    fun findByInvoiceId(invoiceId: UUID): List<BatchItem>
}
