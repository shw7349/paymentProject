package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.PaymentMethod

/**
 * PG 연동 포트(전략 인터페이스). 모든 PG 어댑터가 이 하나의 계약을 구현한다.
 *
 * 공통 로직([PaymentGatewayRouter], Kafka 리스너)은 이 포트에만 의존하므로, PG 를 새로 붙일 때는
 * 이 인터페이스를 구현한 어댑터 빈 하나를 추가하면 끝이다. 라우터가 스프링 컨텍스트에서 모든
 * `PaymentGateway` 빈을 자동으로 수집하므로 등록 코드조차 손댈 필요가 없다.
 */
interface PaymentGateway {
    /** 이 어댑터가 대표하는 PG. */
    val provider: PgProvider

    /** 이 PG 가 해당 결제수단을 처리할 수 있는지. 결제수단이 늘어도 각 어댑터만 답하면 된다. */
    fun supports(method: PaymentMethod): Boolean

    /** 승인 요청. 벤더 응답을 공통 [PgApprovalResponse] 로 정규화해서 돌려준다. */
    fun approve(command: PgApprovalCommand): PgApprovalResponse

    /** 승인 취소(보상 트랜잭션). */
    fun cancel(command: PgCancelCommand): PgCancelResponse
}
