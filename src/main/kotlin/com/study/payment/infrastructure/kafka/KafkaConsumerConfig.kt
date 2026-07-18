package com.study.payment.infrastructure.kafka

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Retries a failing listener 3 times (1s apart) before publishing the record to a
 * `<topic>.DLT` dead-letter topic instead of blocking the partition forever.
 * Spring Boot auto-wires this [DefaultErrorHandler] bean into the listener container factory.
 */
@Configuration
class KafkaConsumerConfig {

    @Bean
    fun kafkaErrorHandler(kafkaOperations: KafkaOperations<Any, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaOperations) { record, _ ->
            TopicPartition("${record.topic()}.DLT", record.partition())
        }
        val backOff = FixedBackOff(1000L, 3L)
        return DefaultErrorHandler(recoverer, backOff)
    }
}
