package com.smarttreasury.repository

import com.smarttreasury.model.Invoice
import com.smarttreasury.model.InvoiceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByStatus(status: InvoiceStatus): List<Invoice>
    fun findByCurrency(currency: String): List<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate <= :date")
    fun findByStatusAndDueDateBefore(status: InvoiceStatus, date: LocalDate): List<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.status = 'PENDING' AND i.dueDate BETWEEN :start AND :end")
    fun findPendingBetweenDates(start: LocalDate, end: LocalDate): List<Invoice>

    fun countByStatus(status: InvoiceStatus): Long
}
