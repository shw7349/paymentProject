package com.study.payment.domain.payment

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentTest {

    private fun money(amount: String = "1000") = Money(BigDecimal(amount), "KRW")

    @Test
    fun `request creates a payment in REQUESTED status`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)

        assertEquals(PaymentStatus.REQUESTED, payment.status)
        assertEquals("order-1", payment.orderId)
        assertNull(payment.approvedAt)
        assertNull(payment.failedAt)
    }

    @Test
    fun `request rejects a blank orderId`() {
        assertFailsWith<IllegalArgumentException> {
            Payment.request("  ", money(), PaymentMethod.CARD)
        }
    }

    @Test
    fun `markProcessing moves REQUESTED to PROCESSING`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)

        payment.markProcessing()

        assertEquals(PaymentStatus.PROCESSING, payment.status)
    }

    @Test
    fun `markProcessing fails when not REQUESTED`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)
        payment.markProcessing()

        assertFailsWith<InvalidPaymentStateException> { payment.markProcessing() }
    }

    @Test
    fun `approve transitions REQUESTED or PROCESSING to APPROVED and stamps approvedAt`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)

        payment.approve()

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertNotNull(payment.approvedAt)
    }

    @Test
    fun `approve fails from a terminal state`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)
        payment.approve()

        assertFailsWith<InvalidPaymentStateException> { payment.approve() }
    }

    @Test
    fun `fail transitions REQUESTED or PROCESSING to FAILED with a reason`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)

        payment.fail("PG_DECLINED")

        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals("PG_DECLINED", payment.failureReason)
        assertNotNull(payment.failedAt)
    }

    @Test
    fun `fail fails from a terminal state`() {
        val payment = Payment.request("order-1", money(), PaymentMethod.CARD)
        payment.fail("PG_DECLINED")

        assertFailsWith<InvalidPaymentStateException> { payment.fail("ANOTHER_REASON") }
    }
}
