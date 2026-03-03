package com.smarttreasury.controller

import com.smarttreasury.dto.DashboardSummary
import com.smarttreasury.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<DashboardSummary> {
        return ResponseEntity.ok(dashboardService.getSummary())
    }
}
