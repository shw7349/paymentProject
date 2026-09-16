package com.study.payment.infrastructure.pg.adapter

import com.study.payment.domain.payment.PaymentMethod
import com.study.payment.infrastructure.pg.PaymentGateway
import com.study.payment.infrastructure.pg.PgApprovalCommand
import com.study.payment.infrastructure.pg.PgApprovalResponse
import com.study.payment.infrastructure.pg.PgCancelCommand
import com.study.payment.infrastructure.pg.PgCancelResponse
import com.study.payment.infrastructure.pg.PgProvider
import com.study.payment.infrastructure.pg.vendor.OrbitPayApi
import com.study.payment.infrastructure.pg.vendor.OrbitPayCharge
import com.study.payment.infrastructure.pg.vendor.OrbitPayException
import org.springframework.stereotype.Component

/**
 * OrbitPay 어댑터. 성공은 영수증, 실패는 예외로 표현하는 [OrbitPayApi] 를 try/catch 로 감싸
 * 공통 모델의 성공/실패로 정규화한다. OrbitPay 는 가상계좌만 지원한다고 가정한다.
 */
@Component
class OrbitPayGateway(
    private val orbitPayApi: OrbitPayApi
) : PaymentGateway {

    override val provider = PgProvider.ORBIT_PAY

    override fun supports(method: PaymentMethod): Boolean = method == PaymentMethod.VIRTUAL_ACCOUNT

    override fun approve(command: PgApprovalCommand): PgApprovalResponse =
        try {
            val receipt = orbitPayApi.charge(
                OrbitPayCharge(
                    referenceId = command.orderId,
                    value = command.money.amount,
                    currencyUnit = command.money.currency
                )
            )
            PgApprovalResponse.approved(provider, receipt.receiptId)
        } catch (e: OrbitPayException) {
            PgApprovalResponse.declined(provider, e.code, e.detail)
        }

    override fun cancel(command: PgCancelCommand): PgCancelResponse =
        try {
            orbitPayApi.voidCharge(command.pgTransactionId)
            PgCancelResponse(canceled = true, provider = provider)
        } catch (e: OrbitPayException) {
            PgCancelResponse(canceled = false, provider = provider, failureReason = e.detail)
        }
}
