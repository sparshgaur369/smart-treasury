package com.smarttreasury.controller

import com.smarttreasury.dto.OptimizeResponse
import com.smarttreasury.dto.PaymentBatchResponse
import com.smarttreasury.service.BatchService
import com.smarttreasury.service.BatchingEngine
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/batches")
class BatchController(
    private val batchingEngine: BatchingEngine,
    private val batchService: BatchService
) {

    @PostMapping("/optimize")
    fun optimize(): ResponseEntity<OptimizeResponse> {
        return ResponseEntity.ok(batchingEngine.optimize())
    }

    @GetMapping
    fun getAllBatches(): ResponseEntity<List<PaymentBatchResponse>> {
        return ResponseEntity.ok(batchService.getAllBatches())
    }

    @PostMapping("/{id}/approve")
    fun approveBatch(@PathVariable id: UUID): ResponseEntity<PaymentBatchResponse> {
        val batch = batchService.approveBatch(id)
        return if (batch != null) ResponseEntity.ok(batch)
        else ResponseEntity.notFound().build()
    }

    @PostMapping("/{id}/execute")
    fun executeBatch(@PathVariable id: UUID): ResponseEntity<PaymentBatchResponse> {
        val batch = batchService.executeBatch(id)
        return if (batch != null) ResponseEntity.ok(batch)
        else ResponseEntity.notFound().build()
    }
}
