package com.smarttreasury.repository

import com.smarttreasury.model.Vendor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VendorRepository : JpaRepository<Vendor, UUID>
