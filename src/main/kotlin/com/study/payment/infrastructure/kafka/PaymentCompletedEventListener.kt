package com.study.payment.infrastructure.kafka

import com.study.payment.domain.event.PaymentApprovedEvent
import com.study.payment.domain.event.PaymentCompletedEvent
import com.study.payment.domain.event.PaymentFailedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Stand-in for a downstream subscriber (e.g. notifications, settlement, analytics) that only
 * needs to react to the final outcome of a payment, decoupled from the approval flow itself.
 */
@Component
class PaymentCompletedEventListener {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPLETED], groupId = "payment-notification-group")
    fun handle(event: PaymentCompletedEvent) {
        when (event) {
            is PaymentApprovedEvent ->
                log.info("[Notification] payment approved orderId={} approvedAt={}", event.orderId, event.approvedAt)
            is PaymentFailedEvent ->
                log.info("[Notification] payment failed orderId={} reason={}", event.orderId, event.reason)
        }
    }
}
