package com.study.payment.application

import com.study.payment.domain.payment.Payment
import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.Instant

data class PaymentResult(
    val paymentId: Long,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethod,
    val status: PaymentStatus,
    val requestedAt: Instant
) {
    companion object {
        fun from(payment: Payment): PaymentResult =
            PaymentResult(
                paymentId = requireNotNull(payment.id) { "payment must be persisted" },
                orderId = payment.orderId,
                amount = payment.money.amount,
                currency = payment.money.currency,
                paymentMethod = payment.paymentMethod,
                status = payment.status,
                requestedAt = payment.requestedAt
            )
    }
}
