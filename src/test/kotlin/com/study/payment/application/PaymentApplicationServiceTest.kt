package com.study.payment.application

import com.study.payment.domain.event.PaymentRequestedEvent
import com.study.payment.domain.payment.DuplicatePaymentException
import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.Payment
import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.domain.payment.PaymentNotFoundException
import com.study.payment.domain.payment.PaymentRepository
import com.study.payment.domain.payment.PaymentStatus
import com.study.payment.domain.payment.PaymentTestFactory
import com.study.payment.infrastructure.kafka.PaymentEventProducer
import com.study.payment.infrastructure.lock.DistributedLockExecutor
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentApplicationServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val lockExecutor = mockk<DistributedLockExecutor>()
    private val eventProducer = mockk<PaymentEventProducer>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val service = PaymentApplicationService(paymentRepository, lockExecutor, eventProducer, transactionTemplate)

    private val command = RequestPaymentCommand(
        orderId = "order-1",
        amount = BigDecimal("1000"),
        currency = "KRW",
        paymentMethod = PaymentMethod.CARD
    )

    @BeforeEach
    fun setUp() {
        // let the lock and transaction wrappers just run the action synchronously
        every { lockExecutor.executeWithLock<PaymentResult>(any(), any(), any(), any()) } answers {
            arg<() -> PaymentResult>(3).invoke()
        }
        every { transactionTemplate.execute<PaymentResult>(any()) } answers {
            firstArg<TransactionCallback<PaymentResult>>().doInTransaction(mockk<TransactionStatus>(relaxed = true))
        }
    }

    @Test
    fun `creates a new payment and publishes a requested event when none exists yet`() {
        every { paymentRepository.findByOrderId("order-1") } returns null
        // capture whatever entity the service constructs, simulate persistence by assigning an id
        every { paymentRepository.saveAndFlush(any()) } answers {
            PaymentTestFactory.persisted(firstArg(), id = 42L)
        }
        every { eventProducer.publishRequested(any()) } just Runs

        val result = service.requestPayment(command)

        assertEquals(42L, result.paymentId)
        assertEquals(PaymentStatus.REQUESTED, result.status)
        assertEquals("order-1", result.orderId)
        verify(exactly = 1) { eventProducer.publishRequested(match<PaymentRequestedEvent> { it.orderId == "order-1" }) }
    }

    @Test
    fun `returns the existing payment idempotently without creating a duplicate`() {
        val existing = PaymentTestFactory.persisted(
            Payment.request("order-1", Money(BigDecimal("1000"), "KRW"), PaymentMethod.CARD),
            id = 7L
        )
        every { paymentRepository.findByOrderId("order-1") } returns existing

        val result = service.requestPayment(command)

        assertEquals(7L, result.paymentId)
        verify(exactly = 0) { paymentRepository.saveAndFlush(any()) }
        verify(exactly = 0) { eventProducer.publishRequested(any()) }
    }

    @Test
    fun `translates a DB unique constraint violation into a DuplicatePaymentException`() {
        every { paymentRepository.findByOrderId("order-1") } returns null
        every { paymentRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate key")

        assertFailsWith<DuplicatePaymentException> { service.requestPayment(command) }
        verify(exactly = 0) { eventProducer.publishRequested(any()) }
    }

    @Test
    fun `getPayment returns the payment for an existing orderId`() {
        val existing = PaymentTestFactory.persisted(
            Payment.request("order-1", Money(BigDecimal("1000"), "KRW"), PaymentMethod.CARD),
            id = 7L
        )
        every { paymentRepository.findByOrderId("order-1") } returns existing

        val result = service.getPayment("order-1")

        assertEquals(7L, result.paymentId)
    }

    @Test
    fun `getPayment throws when the orderId is unknown`() {
        every { paymentRepository.findByOrderId("missing") } returns null

        assertFailsWith<PaymentNotFoundException> { service.getPayment("missing") }
    }
}
