package com.study.payment.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant

/**
 * Aggregate root. `orderId` carries a DB-level unique constraint (see V1 migration) which acts
 * as the last line of defense against duplicate payments if the Redis lock is ever bypassed
 * (lock expiry, node failure, bug, etc).
 */
@Entity
@Table(
    name = "payments",
    uniqueConstraints = [UniqueConstraint(name = "uk_payments_order_id", columnNames = ["order_id"])]
)
class Payment private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    val orderId: String,

    @Embedded
    val money: Money,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    val paymentMethod: PaymentMethod,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.REQUESTED,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: Instant = Instant.now(),

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "failed_at")
    var failedAt: Instant? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
) {
    fun markProcessing() {
        if (status != PaymentStatus.REQUESTED) throw InvalidPaymentStateException(status, "start processing")
        status = PaymentStatus.PROCESSING
    }

    fun approve() {
        if (status != PaymentStatus.REQUESTED && status != PaymentStatus.PROCESSING) {
            throw InvalidPaymentStateException(status, "approve")
        }
        status = PaymentStatus.APPROVED
        approvedAt = Instant.now()
    }

    fun fail(reason: String) {
        if (status != PaymentStatus.REQUESTED && status != PaymentStatus.PROCESSING) {
            throw InvalidPaymentStateException(status, "fail")
        }
        status = PaymentStatus.FAILED
        failureReason = reason
        failedAt = Instant.now()
    }

    companion object {
        fun request(orderId: String, money: Money, paymentMethod: PaymentMethod): Payment {
            require(orderId.isNotBlank()) { "orderId must not be blank" }
            return Payment(orderId = orderId, money = money, paymentMethod = paymentMethod)
        }
    }
}
