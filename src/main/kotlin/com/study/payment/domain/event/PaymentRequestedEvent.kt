package com.study.payment.domain.event

import com.study.payment.domain.payment.Payment
import com.study.payment.domain.payment.PaymentMethod
import java.math.BigDecimal
import java.time.Instant

data class PaymentRequestedEvent(
    val paymentId: Long,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethod,
    val occurredAt: Instant = Instant.now()
) {
    companion object {
        fun from(payment: Payment): PaymentRequestedEvent =
            PaymentRequestedEvent(
                paymentId = requireNotNull(payment.id) { "payment must be persisted before publishing an event" },
                orderId = payment.orderId,
                amount = payment.money.amount,
                currency = payment.money.currency,
                paymentMethod = payment.paymentMethod
            )
    }
}
