package com.study.payment.infrastructure.pg.adapter

import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.infrastructure.pg.PaymentGateway
import com.study.payment.infrastructure.pg.PgApprovalCommand
import com.study.payment.infrastructure.pg.PgApprovalResponse
import com.study.payment.infrastructure.pg.PgCancelCommand
import com.study.payment.infrastructure.pg.PgCancelResponse
import com.study.payment.infrastructure.pg.PgProvider
import com.study.payment.infrastructure.pg.vendor.NovaPayApi
import com.study.payment.infrastructure.pg.vendor.NovaPayRequest
import org.springframework.stereotype.Component

/**
 * NovaPay 어댑터. 결과 코드("0000"=성공) 방식의 [NovaPayApi] 응답을 공통 모델로 변환한다.
 * NovaPay 는 카드 결제만 지원한다고 가정한다.
 */
@Component
class NovaPayGateway(
    private val novaPayApi: NovaPayApi
) : PaymentGateway {

    override val provider = PgProvider.NOVA_PAY

    override fun supports(method: PaymentMethod): Boolean = method == PaymentMethod.CARD

    override fun approve(command: PgApprovalCommand): PgApprovalResponse {
        val response = novaPayApi.requestApproval(
            NovaPayRequest(
                merchantOrderId = command.orderId,
                amount = command.money.amount,
                currencyCode = command.money.currency
            )
        )
        return if (response.resultCode == SUCCESS_CODE && response.tid != null) {
            PgApprovalResponse.approved(provider, response.tid)
        } else {
            PgApprovalResponse.declined(provider, response.resultCode, "NovaPay declined")
        }
    }

    override fun cancel(command: PgCancelCommand): PgCancelResponse {
        val response = novaPayApi.cancelApproval(command.pgTransactionId)
        return PgCancelResponse(canceled = response.resultCode == SUCCESS_CODE, provider = provider)
    }

    companion object {
        private const val SUCCESS_CODE = "0000"
    }
}
