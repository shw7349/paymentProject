package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.infrastructure.pg.adapter.LunaPayGateway
import com.study.payment.infrastructure.pg.adapter.NovaPayGateway
import com.study.payment.infrastructure.pg.adapter.OrbitPayGateway
import com.study.payment.infrastructure.pg.vendor.LunaPayApi
import com.study.payment.infrastructure.pg.vendor.LunaPayResult
import com.study.payment.infrastructure.pg.vendor.NovaPayApi
import com.study.payment.infrastructure.pg.vendor.NovaPayResponse
import com.study.payment.infrastructure.pg.vendor.OrbitPayApi
import com.study.payment.infrastructure.pg.vendor.OrbitPayException
import com.study.payment.infrastructure.pg.vendor.OrbitPayReceipt
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 각 어댑터가 서로 다른 벤더 응답(코드/상태문자열/예외)을 하나의 공통 [PgApprovalResponse] 로
 * 정규화하는지 검증한다. 벤더 API 는 목킹해 결과를 고정하므로 매핑 로직만 결정적으로 확인한다.
 */
class PgAdapterMappingTest {

    private val command = PgApprovalCommand("order-1", Money(BigDecimal("1000"), "KRW"), PaymentMethod.CARD)

    @Test
    fun `NovaPay - 결과코드 0000 은 승인으로 매핑된다`() {
        val api = mockk<NovaPayApi>()
        every { api.requestApproval(any()) } returns NovaPayResponse(resultCode = "0000", tid = "NOVA-1")
        val response = NovaPayGateway(api).approve(command)

        assertTrue(response.approved)
        assertEquals(PgProvider.NOVA_PAY, response.provider)
        assertEquals("NOVA-1", response.pgTransactionId)
    }

    @Test
    fun `NovaPay - 0000 이 아니면 거절로 매핑되고 원본 코드를 보존한다`() {
        val api = mockk<NovaPayApi>()
        every { api.requestApproval(any()) } returns NovaPayResponse(resultCode = "0110", tid = null)
        val response = NovaPayGateway(api).approve(command)

        assertFalse(response.approved)
        assertEquals("0110", response.failureCode)
    }

    @Test
    fun `LunaPay - APPROVED 상태는 승인으로 매핑된다`() {
        val api = mockk<LunaPayApi>()
        every { api.pay(any()) } returns LunaPayResult(status = "APPROVED", transactionKey = "LUNA-1", message = "ok")
        val response = LunaPayGateway(api).approve(command)

        assertTrue(response.approved)
        assertEquals("LUNA-1", response.pgTransactionId)
    }

    @Test
    fun `LunaPay - DECLINED 상태는 거절로 매핑되고 메시지를 보존한다`() {
        val api = mockk<LunaPayApi>()
        every { api.pay(any()) } returns LunaPayResult(status = "DECLINED", transactionKey = null, message = "insufficient balance")
        val response = LunaPayGateway(api).approve(command)

        assertFalse(response.approved)
        assertEquals("insufficient balance", response.failureReason)
    }

    @Test
    fun `OrbitPay - 영수증 반환은 승인으로 매핑된다`() {
        val api = mockk<OrbitPayApi>()
        every { api.charge(any()) } returns OrbitPayReceipt(receiptId = "ORBIT-1")
        val response = OrbitPayGateway(api).approve(command)

        assertTrue(response.approved)
        assertEquals("ORBIT-1", response.pgTransactionId)
    }

    @Test
    fun `OrbitPay - 예외는 거절로 매핑된다`() {
        val api = mockk<OrbitPayApi>()
        every { api.charge(any()) } throws OrbitPayException(code = "E_DECLINED", detail = "card issuer declined")
        val response = OrbitPayGateway(api).approve(command)

        assertFalse(response.approved)
        assertEquals("E_DECLINED", response.failureCode)
        assertEquals("card issuer declined", response.failureReason)
    }
}
