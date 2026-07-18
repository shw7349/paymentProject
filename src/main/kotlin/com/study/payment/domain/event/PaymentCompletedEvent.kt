package com.study.payment.domain.event

import com.study.payment.domain.payment.Payment
import java.time.Instant

sealed interface PaymentCompletedEvent {
    val paymentId: Long
    val orderId: String
}

data class PaymentApprovedEvent(
    override val paymentId: Long,
    override val orderId: String,
    val approvedAt: Instant = Instant.now()
) : PaymentCompletedEvent {
    companion object {
        fun from(payment: Payment): PaymentApprovedEvent =
            PaymentApprovedEvent(
                paymentId = requireNotNull(payment.id) { "payment must be persisted before publishing an event" },
                orderId = payment.orderId,
                approvedAt = payment.approvedAt ?: Instant.now()
            )
    }
}

data class PaymentFailedEvent(
    override val paymentId: Long,
    override val orderId: String,
    val reason: String,
    val failedAt: Instant = Instant.now()
) : PaymentCompletedEvent {
    companion object {
        fun from(payment: Payment): PaymentFailedEvent =
            PaymentFailedEvent(
                paymentId = requireNotNull(payment.id) { "payment must be persisted before publishing an event" },
                orderId = payment.orderId,
                reason = payment.failureReason ?: "UNKNOWN_ERROR",
                failedAt = payment.failedAt ?: Instant.now()
            )
    }
}
