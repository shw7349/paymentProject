package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Fake external PG approval used for local/study purposes: simulates network latency and an
 * ~10% decline rate so both the APPROVED and FAILED flows can be exercised end to end.
 */
@Component
class MockPgClient : PgClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun approve(orderId: String, money: Money): PgApprovalResult {
        Thread.sleep(Random.nextLong(50L, 300L))
        val approved = Random.nextInt(100) >= 10
        log.info("PG approval simulated for orderId={} amount={} -> {}", orderId, money.amount, approved)
        return if (approved) {
            PgApprovalResult(isSuccess = true)
        } else {
            PgApprovalResult(isSuccess = false, failureReason = "PG_DECLINED")
        }
    }
}
