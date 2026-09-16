package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.Money
import com.study.payment.domain.payment.PaymentMethod

/**
 * PG 승인 요청의 공통 입력 모델. 특정 PG 의 프로토콜에 종속되지 않는 우리 도메인의 언어로만 표현한다.
 * 각 어댑터가 이 값을 자기 벤더 API 의 요청 형태로 변환한다.
 */
data class PgApprovalCommand(
    val orderId: String,
    val money: Money,
    val paymentMethod: PaymentMethod
)
