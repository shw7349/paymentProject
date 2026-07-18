package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.Money

data class PgApprovalResult(
    val isSuccess: Boolean,
    val failureReason: String? = null
)

/** Stand-in for a real PG (payment gateway) integration. */
interface PgClient {
    fun approve(orderId: String, money: Money): PgApprovalResult
}
