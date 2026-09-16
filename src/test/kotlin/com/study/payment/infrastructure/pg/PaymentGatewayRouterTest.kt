package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.PaymentMethod
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 라우터가 정책에 따라 올바른 PG 어댑터를 고르고 위임하는지, 그리고 잘못된 라우팅을 방어하는지 검증한다.
 * PG 를 추가/교체해도 이 공통 로직이 그대로 동작함을 보여주기 위해 실제 어댑터가 아닌 페이크로 시험한다.
 */
class PaymentGatewayRouterTest {

    private fun command(method: PaymentMethod) =
        PgApprovalCommand("order-1", Money(BigDecimal("1000"), "KRW"), method)

    private class FakeGateway(
        override val provider: PgProvider,
        private val supported: Set<PaymentMethod>
    ) : PaymentGateway {
        var approveCalls = 0
        override fun supports(method: PaymentMethod) = method in supported
        override fun approve(command: PgApprovalCommand): PgApprovalResponse {
            approveCalls++
            return PgApprovalResponse.approved(provider, "tx-$provider")
        }
        override fun cancel(command: PgCancelCommand) = PgCancelResponse(canceled = true, provider = provider)
    }

    @Test
    fun `정책이 지정한 provider 의 어댑터로 승인을 위임한다`() {
        val nova = FakeGateway(PgProvider.NOVA_PAY, setOf(PaymentMethod.CARD))
        val luna = FakeGateway(PgProvider.LUNA_PAY, setOf(PaymentMethod.CARD, PaymentMethod.VIRTUAL_ACCOUNT))
        val policy = mockk<PgRoutingPolicy>()
        every { policy.providerFor(PaymentMethod.CARD) } returns PgProvider.NOVA_PAY
        every { policy.providerFor(PaymentMethod.VIRTUAL_ACCOUNT) } returns PgProvider.LUNA_PAY
        val router = PaymentGatewayRouter(listOf(nova, luna), policy)

        val cardResponse = router.approve(command(PaymentMethod.CARD))
        val vaResponse = router.approve(command(PaymentMethod.VIRTUAL_ACCOUNT))

        assertEquals(PgProvider.NOVA_PAY, cardResponse.provider)
        assertEquals(PgProvider.LUNA_PAY, vaResponse.provider)
        assertEquals(1, nova.approveCalls)
        assertEquals(1, luna.approveCalls)
    }

    @Test
    fun `정책이 결제수단을 지원하지 않는 PG 를 가리키면 거부한다`() {
        val nova = FakeGateway(PgProvider.NOVA_PAY, setOf(PaymentMethod.CARD)) // 가상계좌 미지원
        val policy = mockk<PgRoutingPolicy>()
        every { policy.providerFor(PaymentMethod.VIRTUAL_ACCOUNT) } returns PgProvider.NOVA_PAY
        val router = PaymentGatewayRouter(listOf(nova), policy)

        assertFailsWith<IllegalArgumentException> { router.approve(command(PaymentMethod.VIRTUAL_ACCOUNT)) }
    }

    @Test
    fun `등록되지 않은 provider 로 라우팅되면 예외를 던진다`() {
        val luna = FakeGateway(PgProvider.LUNA_PAY, setOf(PaymentMethod.CARD))
        val policy = mockk<PgRoutingPolicy>()
        every { policy.providerFor(PaymentMethod.CARD) } returns PgProvider.NOVA_PAY // 어댑터 미등록
        val router = PaymentGatewayRouter(listOf(luna), policy)

        assertFailsWith<IllegalStateException> { router.approve(command(PaymentMethod.CARD)) }
    }
}
