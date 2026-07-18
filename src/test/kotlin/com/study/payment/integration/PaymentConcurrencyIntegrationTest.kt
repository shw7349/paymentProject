package com.study.payment.integration

import com.study.payment.TestcontainersConfiguration
import com.study.payment.application.PaymentApplicationService
import com.study.payment.application.RequestPaymentCommand
import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.Payment
import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.domain.payment.PaymentRepository
import com.study.payment.domain.payment.PaymentStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the two independent concurrency defenses end to end against real MySQL, Redis and
 * Kafka (via Testcontainers, wired through [TestcontainersConfiguration]):
 *
 *  1. The Redis distributed lock serializes concurrent requests for the same orderId.
 *  2. The DB unique constraint on `payments.order_id` is the last line of defense, and holds
 *     up even if callers bypass the lock entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration::class)
class PaymentConcurrencyIntegrationTest {

    @Autowired
    private lateinit var paymentApplicationService: PaymentApplicationService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Test
    fun `concurrent requests for the same orderId through the redis lock yield exactly one payment`() {
        val orderId = "order-concurrent-${UUID.randomUUID()}"
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val paymentIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val result = paymentApplicationService.requestPayment(
                        RequestPaymentCommand(orderId, BigDecimal("1000"), "KRW", PaymentMethod.CARD)
                    )
                    paymentIds.add(result.paymentId)
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(errors.isEmpty(), "no request should fail under a healthy lock: $errors")
        assertEquals(1, paymentIds.size, "all concurrent requests must resolve to the same single payment")

        val rows = paymentRepository.findAll().count { it.orderId == orderId }
        assertEquals(1, rows, "exactly one row must exist for the orderId")

        val finalStatus = awaitFinalStatus(orderId)
        assertTrue(
            finalStatus == PaymentStatus.APPROVED || finalStatus == PaymentStatus.FAILED,
            "the async Kafka consumer should move the payment to a terminal state, was $finalStatus"
        )
    }

    @Test
    fun `DB unique constraint blocks duplicate inserts even without the redis lock`() {
        val orderId = "order-db-only-${UUID.randomUUID()}"
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successes = AtomicInteger(0)
        val duplicateRejections = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    paymentRepository.saveAndFlush(
                        Payment.request(orderId, Money(BigDecimal("500"), "KRW"), PaymentMethod.CARD)
                    )
                    successes.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    duplicateRejections.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, successes.get(), "only the first insert should succeed")
        assertEquals(threadCount - 1, duplicateRejections.get(), "every other insert must be rejected by the unique constraint")
    }

    private fun awaitFinalStatus(orderId: String, timeoutSeconds: Long = 20): PaymentStatus {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            val payment = paymentRepository.findByOrderId(orderId)
            if (payment != null && payment.status != PaymentStatus.REQUESTED && payment.status != PaymentStatus.PROCESSING) {
                return payment.status
            }
            Thread.sleep(200)
        }
        val payment = assertNotNull(paymentRepository.findByOrderId(orderId))
        error("payment $orderId did not reach a final state within ${timeoutSeconds}s, was ${payment.status}")
    }
}
