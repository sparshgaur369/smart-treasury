package com.smarttreasury.controller

import com.smarttreasury.dto.CreateInvoiceRequest
import com.smarttreasury.dto.InvoiceResponse
import com.smarttreasury.service.InvoiceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/invoices")
class InvoiceController(private val invoiceService: InvoiceService) {

    @PostMapping
    fun createInvoice(@RequestBody request: CreateInvoiceRequest): ResponseEntity<InvoiceResponse> {
        val invoice = invoiceService.createInvoice(request)
        return ResponseEntity.ok(invoice)
    }

    @GetMapping
    fun getInvoices(@RequestParam(required = false) status: String?): ResponseEntity<List<InvoiceResponse>> {
        return ResponseEntity.ok(invoiceService.getInvoices(status))
    }

    @GetMapping("/{id}")
    fun getInvoiceById(@PathVariable id: UUID): ResponseEntity<InvoiceResponse> {
        val invoice = invoiceService.getInvoiceById(id)
        return if (invoice != null) ResponseEntity.ok(invoice)
        else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteInvoice(@PathVariable id: UUID): ResponseEntity<Void> {
        return if (invoiceService.deleteInvoice(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }
}
