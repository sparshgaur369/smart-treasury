package com.smarttreasury.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HTTP client for the Frankfurter API (https://api.frankfurter.app).
 * Free, no API key needed. Provides ECB-published exchange rates.
 */
@Component
class FrankfurterClient {

    private val logger = LoggerFactory.getLogger(FrankfurterClient::class.java)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()
    private val baseUrl = "https://api.frankfurter.app"

    /**
     * Fetch latest exchange rates for a base currency against target currencies.
     * Example: getLatestRates("USD", listOf("EUR", "INR", "GBP", "BRL"))
     * Returns: Map of target currency code → rate
     */
    fun getLatestRates(base: String, targets: List<String>): Map<String, BigDecimal> {
        return try {
            val targetsParam = targets.joinToString(",")
            val url = "$baseUrl/latest?base=$base&symbols=$targetsParam"
            logger.debug("Fetching latest rates: $url")

            val response = restTemplate.getForObject(url, String::class.java)
            val json = objectMapper.readTree(response)
            val rates = json.get("rates")

            val result = mutableMapOf<String, BigDecimal>()
            rates.fields().forEach { (key, value) ->
                result[key] = BigDecimal(value.asText())
            }
            logger.info("Fetched ${result.size} rates for base=$base")
            result
        } catch (e: Exception) {
            logger.error("Failed to fetch latest rates from Frankfurter API", e)
            emptyMap()
        }
    }

    /**
     * Fetch historical rates for a currency pair over a date range.
     * Returns: List of Pair(date, rate) sorted chronologically
     */
    fun getHistoricalRates(
        base: String,
        target: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Pair<LocalDate, BigDecimal>> {
        return try {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val url = "$baseUrl/${startDate.format(formatter)}..${endDate.format(formatter)}?base=$base&symbols=$target"
            logger.debug("Fetching historical rates: $url")

            val response = restTemplate.getForObject(url, String::class.java)
            val json = objectMapper.readTree(response)
            val rates = json.get("rates")

            val result = mutableListOf<Pair<LocalDate, BigDecimal>>()
            rates.fields().forEach { (dateStr, ratesNode) ->
                val date = LocalDate.parse(dateStr, formatter)
                val rate = BigDecimal(ratesNode.get(target).asText())
                result.add(Pair(date, rate))
            }
            result.sortBy { it.first }
            logger.info("Fetched ${result.size} historical rates for $base/$target")
            result
        } catch (e: Exception) {
            logger.error("Failed to fetch historical rates from Frankfurter API", e)
            emptyList()
        }
    }
}
