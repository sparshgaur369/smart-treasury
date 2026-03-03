package com.smarttreasury.controller

import com.smarttreasury.dto.FxHistoryResponse
import com.smarttreasury.dto.FxRateResponse
import com.smarttreasury.dto.FxVolatilityResponse
import com.smarttreasury.service.FxRateService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/fx")
class FxRateController(private val fxRateService: FxRateService) {

    @GetMapping("/latest")
    fun getLatestRates(
        @RequestParam(defaultValue = "USD") base: String,
        @RequestParam targets: String
    ): ResponseEntity<List<FxRateResponse>> {
        val targetList = targets.split(",").map { it.trim() }
        return ResponseEntity.ok(fxRateService.getLatestRates(base, targetList))
    }

    @GetMapping("/history")
    fun getHistory(
        @RequestParam pair: String,
        @RequestParam(defaultValue = "90") days: Int
    ): ResponseEntity<FxHistoryResponse> {
        return ResponseEntity.ok(fxRateService.getHistory(pair, days))
    }

    @GetMapping("/volatility")
    fun getVolatility(@RequestParam pair: String): ResponseEntity<FxVolatilityResponse> {
        return ResponseEntity.ok(fxRateService.getVolatility(pair))
    }
}
