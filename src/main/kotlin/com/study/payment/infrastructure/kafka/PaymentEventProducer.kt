package com.study.payment.infrastructure.kafka

import com.study.payment.domain.event.PaymentCompletedEvent
import com.study.payment.domain.event.PaymentRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Publishes domain events to Kafka. The partition key is always the orderId so that all
 * events for a given order land on the same partition and are processed in order by a
 * single consumer thread within a consumer group.
 */
@Component
class PaymentEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishRequested(event: PaymentRequestedEvent) {
        log.info("Publishing PaymentRequestedEvent orderId={}", event.orderId)
        kafkaTemplate.send(KafkaTopics.PAYMENT_REQUESTED, event.orderId, event)
    }

    fun publishCompleted(event: PaymentCompletedEvent) {
        log.info("Publishing {} orderId={}", event::class.simpleName, event.orderId)
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, event.orderId, event)
    }
}
