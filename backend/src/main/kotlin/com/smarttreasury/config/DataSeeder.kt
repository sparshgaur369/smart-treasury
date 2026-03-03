package com.smarttreasury.config

import com.smarttreasury.model.*
import com.smarttreasury.repository.*
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seeds the database with sample data on first run.
 * Only runs if no vendors exist in the database.
 *
 * Seeds:
 * - 5 vendors across India, Germany, Brazil, UK, USA
 * - 12 pending invoices with realistic amounts and due dates (next 30 days)
 * - 90 days of synthetic FX rate history for volatility calculations
 */
@Component
class DataSeeder(
    private val vendorRepository: VendorRepository,
    private val invoiceRepository: InvoiceRepository,
    private val fxRateSnapshotRepository: FxRateSnapshotRepository
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(args: ApplicationArguments?) {
        if (vendorRepository.count() > 0) {
            logger.info("Database already seeded. Skipping.")
            return
        }

        logger.info("Seeding database with sample data...")
        val vendors = seedVendors()
        seedInvoices(vendors)
        seedFxHistory()
        logger.info("Database seeding complete!")
    }

    private fun seedVendors(): List<Vendor> {
        val vendors = listOf(
            Vendor(
                name = "TechServe India Pvt Ltd",
                country = "India",
                preferredCurrency = "INR",
                bankDetails = mapOf(
                    "bankName" to "HDFC Bank",
                    "accountNumber" to "50100XXXXXXX789",
                    "ifsc" to "HDFC0001234"
                )
            ),
            Vendor(
                name = "Schmidt & Weber GmbH",
                country = "Germany",
                preferredCurrency = "EUR",
                bankDetails = mapOf(
                    "bankName" to "Deutsche Bank",
                    "iban" to "DE89370400440532013000",
                    "bic" to "DEUTDEDB"
                )
            ),
            Vendor(
                name = "NovaTech Brasil LTDA",
                country = "Brazil",
                preferredCurrency = "BRL",
                bankDetails = mapOf(
                    "bankName" to "Banco do Brasil",
                    "accountNumber" to "12345-6",
                    "agency" to "0001"
                )
            ),
            Vendor(
                name = "Albion Digital Solutions Ltd",
                country = "United Kingdom",
                preferredCurrency = "GBP",
                bankDetails = mapOf(
                    "bankName" to "Barclays",
                    "sortCode" to "20-45-67",
                    "accountNumber" to "12345678"
                )
            ),
            Vendor(
                name = "CloudPeak Systems Inc",
                country = "United States",
                preferredCurrency = "USD",
                bankDetails = mapOf(
                    "bankName" to "JPMorgan Chase",
                    "routingNumber" to "021000021",
                    "accountNumber" to "123456789"
                )
            )
        )
        return vendorRepository.saveAll(vendors)
    }

    private fun seedInvoices(vendors: List<Vendor>) {
        val today = LocalDate.now()
        val random = Random(42) // Fixed seed for reproducible data

        val invoices = listOf(
            // INR invoices (TechServe India)
            Invoice(
                vendorId = vendors[0].id!!,
                amount = BigDecimal("285000.00"),
                currency = "INR",
                dueDate = today.plusDays(5),
                description = "Backend development - Sprint 14"
            ),
            Invoice(
                vendorId = vendors[0].id!!,
                amount = BigDecimal("142500.00"),
                currency = "INR",
                dueDate = today.plusDays(8),
                description = "QA testing services - February"
            ),
            Invoice(
                vendorId = vendors[0].id!!,
                amount = BigDecimal("95000.00"),
                currency = "INR",
                dueDate = today.plusDays(12),
                description = "DevOps infrastructure setup"
            ),

            // EUR invoices (Schmidt & Weber)
            Invoice(
                vendorId = vendors[1].id!!,
                amount = BigDecimal("8500.00"),
                currency = "EUR",
                dueDate = today.plusDays(7),
                description = "UI/UX design consultation"
            ),
            Invoice(
                vendorId = vendors[1].id!!,
                amount = BigDecimal("12750.00"),
                currency = "EUR",
                dueDate = today.plusDays(15),
                description = "Frontend development - Phase 2"
            ),

            // BRL invoices (NovaTech Brasil)
            Invoice(
                vendorId = vendors[2].id!!,
                amount = BigDecimal("45000.00"),
                currency = "BRL",
                dueDate = today.plusDays(10),
                description = "Marketing campaign - LATAM region"
            ),
            Invoice(
                vendorId = vendors[2].id!!,
                amount = BigDecimal("28750.00"),
                currency = "BRL",
                dueDate = today.plusDays(18),
                description = "Social media management - March"
            ),

            // GBP invoices (Albion Digital)
            Invoice(
                vendorId = vendors[3].id!!,
                amount = BigDecimal("6200.00"),
                currency = "GBP",
                dueDate = today.plusDays(6),
                description = "Compliance audit services"
            ),
            Invoice(
                vendorId = vendors[3].id!!,
                amount = BigDecimal("4800.00"),
                currency = "GBP",
                dueDate = today.plusDays(20),
                description = "Legal document review"
            ),

            // USD invoices (CloudPeak Systems)
            Invoice(
                vendorId = vendors[4].id!!,
                amount = BigDecimal("15000.00"),
                currency = "USD",
                dueDate = today.plusDays(3),
                description = "AWS hosting - Monthly"
            ),
            Invoice(
                vendorId = vendors[4].id!!,
                amount = BigDecimal("7500.00"),
                currency = "USD",
                dueDate = today.plusDays(14),
                description = "CI/CD pipeline maintenance"
            ),
            Invoice(
                vendorId = vendors[4].id!!,
                amount = BigDecimal("22000.00"),
                currency = "USD",
                dueDate = today.plusDays(25),
                description = "Data analytics platform license"
            )
        )

        invoiceRepository.saveAll(invoices)
        logger.info("Seeded ${invoices.size} invoices")
    }

    /**
     * Generate 90 days of synthetic FX rate history.
     * Uses realistic base rates with sinusoidal variation and random noise
     * to simulate real market behavior with weekly patterns.
     */
    private fun seedFxHistory() {
        // Realistic base rates (as of early 2024 - approximate USD rates)
        val baseRates = mapOf(
            "EUR" to 0.92,
            "INR" to 83.1,
            "GBP" to 0.79,
            "BRL" to 4.95
        )

        val now = Instant.now()
        val random = Random(123)
        var snapshotCount = 0

        for ((currency, baseRate) in baseRates) {
            for (dayOffset in 90 downTo 0) {
                val snapshotTime = now.minus(dayOffset.toLong(), ChronoUnit.DAYS)
                val dayOfYear = snapshotTime.atZone(ZoneOffset.UTC).dayOfYear

                // Sinusoidal variation (simulates macro trends) ± 3%
                val trend = sin(dayOfYear * 0.05) * baseRate * 0.03

                // Weekly pattern (slightly different rates per day of week)
                val dayOfWeek = snapshotTime.atZone(ZoneOffset.UTC).dayOfWeek.value
                val weeklyFactor = when (dayOfWeek) {
                    1 -> -0.001  // Monday: slightly lower
                    2 -> -0.0005
                    3 -> 0.0015  // Wednesday: peak
                    4 -> 0.001   // Thursday
                    5 -> -0.002  // Friday: dip
                    else -> 0.0
                }
                val weeklyVariation = baseRate * weeklyFactor

                // Random noise ± 0.5%
                val noise = (random.nextDouble() - 0.5) * baseRate * 0.01

                val rate = baseRate + trend + weeklyVariation + noise
                val roundedRate = BigDecimal(rate).setScale(8, RoundingMode.HALF_UP)

                fxRateSnapshotRepository.save(
                    FxRateSnapshot(
                        baseCurrency = "USD",
                        targetCurrency = currency,
                        rate = roundedRate,
                        fetchedAt = snapshotTime
                    )
                )
                snapshotCount++
            }
        }
        logger.info("Seeded $snapshotCount FX rate snapshots (90 days × 4 currencies)")
    }
}
