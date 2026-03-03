package com.smarttreasury.controller

import com.smarttreasury.dto.CreateVendorRequest
import com.smarttreasury.dto.VendorResponse
import com.smarttreasury.service.VendorService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vendors")
class VendorController(private val vendorService: VendorService) {

    @PostMapping
    fun createVendor(@RequestBody request: CreateVendorRequest): ResponseEntity<VendorResponse> {
        val vendor = vendorService.createVendor(request)
        return ResponseEntity.ok(vendor)
    }

    @GetMapping
    fun getAllVendors(): ResponseEntity<List<VendorResponse>> {
        return ResponseEntity.ok(vendorService.getAllVendors())
    }
}
