package com.study.payment.infrastructure.kafka

import com.study.payment.domain.event.PaymentApprovedEvent
import com.study.payment.domain.event.PaymentFailedEvent
import com.study.payment.domain.event.PaymentRequestedEvent
import com.study.payment.domain.payment.PaymentRepository
import com.study.payment.domain.payment.PaymentStatus
import com.study.payment.infrastructure.pg.PgClient
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Consumes [PaymentRequestedEvent] asynchronously and drives the actual PG approval. This is
 * the "async processing" half of the flow: the API only creates a PENDING (REQUESTED) row and
 * returns immediately; this listener does the (potentially slow) approval work off the request
 * thread.
 */
@Component
class PaymentRequestedEventListener(
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    private val eventProducer: PaymentEventProducer
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(topics = [KafkaTopics.PAYMENT_REQUESTED], groupId = "payment-approval-group")
    fun handle(event: PaymentRequestedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElse(null)
        if (payment == null) {
            log.warn("Payment not found for paymentId={}, skipping", event.paymentId)
            return
        }
        if (payment.status != PaymentStatus.REQUESTED) {
            log.info("Payment {} already in status {}, skipping duplicate delivery", payment.id, payment.status)
            return
        }

        payment.markProcessing()
        val result = pgClient.approve(payment.orderId, payment.money)

        if (result.isSuccess) {
            payment.approve()
            paymentRepository.save(payment)
            eventProducer.publishCompleted(PaymentApprovedEvent.from(payment))
        } else {
            payment.fail(result.failureReason ?: "UNKNOWN_ERROR")
            paymentRepository.save(payment)
            eventProducer.publishCompleted(PaymentFailedEvent.from(payment))
        }
    }
}
