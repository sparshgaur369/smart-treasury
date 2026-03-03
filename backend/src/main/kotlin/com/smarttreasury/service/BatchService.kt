package com.smarttreasury.service

import com.smarttreasury.dto.BatchItemResponse
import com.smarttreasury.dto.PaymentBatchResponse
import com.smarttreasury.model.BatchStatus
import com.smarttreasury.model.InvoiceStatus
import com.smarttreasury.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class BatchService(
    private val paymentBatchRepository: PaymentBatchRepository,
    private val batchItemRepository: BatchItemRepository,
    private val invoiceRepository: InvoiceRepository,
    private val vendorRepository: VendorRepository
) {

    fun getAllBatches(): List<PaymentBatchResponse> {
        return paymentBatchRepository.findAllByOrderByCreatedAtDesc().map { batch ->
            val items = batchItemRepository.findByBatchId(batch.id!!)
            val invoices = invoiceRepository.findAllById(items.map { it.invoiceId })
            val vendorMap = vendorRepository.findAllById(invoices.map { it.vendorId }).associateBy { it.id }

            val currency = invoices.firstOrNull()?.currency
            val totalAmount = invoices.fold(java.math.BigDecimal.ZERO) { acc, inv -> acc + inv.amount }

            PaymentBatchResponse(
                id = batch.id,
                scheduledDate = batch.scheduledDate,
                currency = currency,
                totalAmount = totalAmount,
                totalUsdEquivalent = batch.totalUsdEquivalent,
                estimatedSavingUsd = batch.estimatedSavingUsd,
                status = batch.status.name,
                items = invoices.map { invoice ->
                    BatchItemResponse(
                        invoiceId = invoice.id!!,
                        vendorName = vendorMap[invoice.vendorId]?.name,
                        amount = invoice.amount,
                        currency = invoice.currency,
                        dueDate = invoice.dueDate,
                        description = invoice.description
                    )
                },
                createdAt = batch.createdAt
            )
        }
    }

    @Transactional
    fun approveBatch(batchId: UUID): PaymentBatchResponse? {
        val batch = paymentBatchRepository.findById(batchId).orElse(null) ?: return null
        val updated = batch.copy(status = BatchStatus.APPROVED)
        val saved = paymentBatchRepository.save(updated)

        // Mark invoices as SCHEDULED
        val items = batchItemRepository.findByBatchId(batchId)
        for (item in items) {
            val invoice = invoiceRepository.findById(item.invoiceId).orElse(null) ?: continue
            invoiceRepository.save(invoice.copy(status = InvoiceStatus.SCHEDULED))
        }

        return getAllBatches().find { it.id == saved.id }
    }

    @Transactional
    fun executeBatch(batchId: UUID): PaymentBatchResponse? {
        val batch = paymentBatchRepository.findById(batchId).orElse(null) ?: return null
        val updated = batch.copy(status = BatchStatus.EXECUTED)
        val saved = paymentBatchRepository.save(updated)

        // Mark invoices as PAID
        val items = batchItemRepository.findByBatchId(batchId)
        for (item in items) {
            val invoice = invoiceRepository.findById(item.invoiceId).orElse(null) ?: continue
            invoiceRepository.save(invoice.copy(status = InvoiceStatus.PAID))
        }

        return getAllBatches().find { it.id == saved.id }
    }
}
