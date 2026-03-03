package com.smarttreasury.service

import com.smarttreasury.dto.CreateInvoiceRequest
import com.smarttreasury.dto.InvoiceResponse
import com.smarttreasury.model.Invoice
import com.smarttreasury.model.InvoiceStatus
import com.smarttreasury.repository.InvoiceRepository
import com.smarttreasury.repository.VendorRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val vendorRepository: VendorRepository,
    private val fxRateService: FxRateService
) {

    fun createInvoice(request: CreateInvoiceRequest): InvoiceResponse {
        val invoice = Invoice(
            vendorId = request.vendorId,
            amount = request.amount,
            currency = request.currency,
            dueDate = request.dueDate,
            status = InvoiceStatus.PENDING,
            description = request.description,
            createdAt = Instant.now()
        )
        val saved = invoiceRepository.save(invoice)
        return saved.toResponse()
    }

    fun getInvoices(status: String?): List<InvoiceResponse> {
        val invoices = if (status != null) {
            invoiceRepository.findByStatus(InvoiceStatus.valueOf(status.uppercase()))
        } else {
            invoiceRepository.findAll()
        }
        return invoices.map { it.toResponse() }
    }

    fun getInvoiceById(id: UUID): InvoiceResponse? {
        return invoiceRepository.findById(id).orElse(null)?.toResponse()
    }

    fun deleteInvoice(id: UUID): Boolean {
        if (invoiceRepository.existsById(id)) {
            invoiceRepository.deleteById(id)
            return true
        }
        return false
    }

    private fun Invoice.toResponse(): InvoiceResponse {
        val vendorName = vendorRepository.findById(vendorId).orElse(null)?.name
        val usdEquiv = if (currency == "USD") amount
        else {
            try {
                val rate = fxRateService.getRate("USD", currency)
                if (rate > BigDecimal.ZERO) amount.divide(rate, 2, RoundingMode.HALF_UP)
                else null
            } catch (e: Exception) { null }
        }

        return InvoiceResponse(
            id = id!!,
            vendorId = vendorId,
            vendorName = vendorName,
            amount = amount,
            currency = currency,
            usdEquivalent = usdEquiv,
            dueDate = dueDate,
            status = status.name,
            description = description,
            createdAt = createdAt
        )
    }
}
