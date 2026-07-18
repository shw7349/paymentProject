package com.study.payment.presentation

import com.study.payment.application.PaymentResult
import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.Instant

data class PaymentResponseDto(
    val paymentId: Long,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethod,
    val status: PaymentStatus,
    val requestedAt: Instant
) {
    companion object {
        fun from(result: PaymentResult): PaymentResponseDto =
            PaymentResponseDto(
                paymentId = result.paymentId,
                orderId = result.orderId,
                amount = result.amount,
                currency = result.currency,
                paymentMethod = result.paymentMethod,
                status = result.status,
                requestedAt = result.requestedAt
            )
    }
}
