package com.smarttreasury

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SmartTreasuryApplication

fun main(args: Array<String>) {
    runApplication<SmartTreasuryApplication>(*args)
}
