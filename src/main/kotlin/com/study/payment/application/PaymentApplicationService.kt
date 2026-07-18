package com.study.payment.application

import com.study.payment.domain.event.PaymentRequestedEvent
import com.study.payment.domain.payment.DuplicatePaymentException
import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.Payment
import com.study.payment.domain.payment.PaymentNotFoundException
import com.study.payment.domain.payment.PaymentRepository
import com.study.payment.infrastructure.kafka.PaymentEventProducer
import com.study.payment.infrastructure.lock.DistributedLockExecutor
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Orchestrates payment requests with two independent layers of concurrency protection:
 *
 * 1. A Redis distributed lock keyed by orderId (see [DistributedLockExecutor]) serializes
 *    concurrent requests for the same order so at most one of them proceeds at a time.
 * 2. A DB unique constraint on `payments.order_id` (see [Payment]) is the final authority:
 *    even if the lock is bypassed, a duplicate insert fails fast with a
 *    [DataIntegrityViolationException], which is translated into [DuplicatePaymentException].
 *
 * The DB transaction is intentionally started *inside* the lock (via [TransactionTemplate]
 * rather than a `@Transactional` method) so the lock is held for as short a time as possible
 * while still covering the whole read-check-then-insert critical section.
 */
@Service
class PaymentApplicationService(
    private val paymentRepository: PaymentRepository,
    private val lockExecutor: DistributedLockExecutor,
    private val eventProducer: PaymentEventProducer,
    private val transactionTemplate: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestPayment(command: RequestPaymentCommand): PaymentResult {
        val lockKey = DistributedLockExecutor.paymentLockKey(command.orderId)
        return lockExecutor.executeWithLock(lockKey) {
            transactionTemplate.execute { createOrReturnExisting(command) }
        }
    }

    private fun createOrReturnExisting(command: RequestPaymentCommand): PaymentResult {
        val existing = paymentRepository.findByOrderId(command.orderId)
        if (existing != null) {
            log.info("Idempotent request for orderId={}; returning existing payment", command.orderId)
            return PaymentResult.from(existing)
        }

        val payment = Payment.request(
            orderId = command.orderId,
            money = Money(command.amount, command.currency),
            paymentMethod = command.paymentMethod
        )

        val saved = try {
            paymentRepository.saveAndFlush(payment)
        } catch (e: DataIntegrityViolationException) {
            log.warn("Duplicate payment insert blocked by DB unique constraint for orderId={}", command.orderId)
            throw DuplicatePaymentException(command.orderId, e)
        }

        eventProducer.publishRequested(PaymentRequestedEvent.from(saved))
        return PaymentResult.from(saved)
    }

    fun getPayment(orderId: String): PaymentResult {
        val payment = paymentRepository.findByOrderId(orderId) ?: throw PaymentNotFoundException(orderId)
        return PaymentResult.from(payment)
    }
}
