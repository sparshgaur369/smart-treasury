package com.smarttreasury.service

import com.smarttreasury.dto.CreateVendorRequest
import com.smarttreasury.dto.VendorResponse
import com.smarttreasury.model.Vendor
import com.smarttreasury.repository.VendorRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VendorService(private val vendorRepository: VendorRepository) {

    fun createVendor(request: CreateVendorRequest): VendorResponse {
        val vendor = Vendor(
            name = request.name,
            country = request.country,
            preferredCurrency = request.preferredCurrency,
            bankDetails = request.bankDetails,
            createdAt = Instant.now()
        )
        val saved = vendorRepository.save(vendor)
        return saved.toResponse()
    }

    fun getAllVendors(): List<VendorResponse> {
        return vendorRepository.findAll().map { it.toResponse() }
    }

    private fun Vendor.toResponse() = VendorResponse(
        id = id!!,
        name = name,
        country = country,
        preferredCurrency = preferredCurrency,
        bankDetails = bankDetails,
        createdAt = createdAt
    )
}
