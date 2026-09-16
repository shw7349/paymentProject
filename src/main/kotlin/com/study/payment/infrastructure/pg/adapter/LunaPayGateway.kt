package com.study.payment.infrastructure.pg.adapter

import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.infrastructure.pg.PaymentGateway
import com.study.payment.infrastructure.pg.PgApprovalCommand
import com.study.payment.infrastructure.pg.PgApprovalResponse
import com.study.payment.infrastructure.pg.PgCancelCommand
import com.study.payment.infrastructure.pg.PgCancelResponse
import com.study.payment.infrastructure.pg.PgProvider
import com.study.payment.infrastructure.pg.vendor.LunaPayApi
import com.study.payment.infrastructure.pg.vendor.LunaPayRequest
import org.springframework.stereotype.Component

/**
 * LunaPay 어댑터. 상태 문자열("APPROVED"/"DECLINED") 방식의 [LunaPayApi] 응답을 공통 모델로 변환한다.
 * LunaPay 는 카드와 가상계좌를 모두 지원한다고 가정한다.
 */
@Component
class LunaPayGateway(
    private val lunaPayApi: LunaPayApi
) : PaymentGateway {

    override val provider = PgProvider.LUNA_PAY

    override fun supports(method: PaymentMethod): Boolean =
        method == PaymentMethod.CARD || method == PaymentMethod.VIRTUAL_ACCOUNT

    override fun approve(command: PgApprovalCommand): PgApprovalResponse {
        val result = lunaPayApi.pay(
            LunaPayRequest(
                orderNo = command.orderId,
                payAmount = command.money.amount,
                currency = command.money.currency
            )
        )
        return if (result.status == APPROVED_STATUS && result.transactionKey != null) {
            PgApprovalResponse.approved(provider, result.transactionKey)
        } else {
            PgApprovalResponse.declined(provider, result.status, result.message)
        }
    }

    override fun cancel(command: PgCancelCommand): PgCancelResponse {
        val result = lunaPayApi.refund(command.pgTransactionId)
        return PgCancelResponse(canceled = result.status == APPROVED_STATUS, provider = provider)
    }

    companion object {
        private const val APPROVED_STATUS = "APPROVED"
    }
}
