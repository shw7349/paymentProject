package com.study.payment.infrastructure.pg

/**
 * 승인된 거래를 취소(환불)하기 위한 공통 모델. 사가 패턴의 보상 트랜잭션에서, 후속 단계가 실패했을 때
 * 이미 성공한 PG 승인을 되돌리는 데 쓴다. 취소도 승인과 마찬가지로 PG 별 프로토콜을 어댑터가 흡수한다.
 */
data class PgCancelCommand(
    val orderId: String,
    /** 되돌릴 원 승인 거래의 식별자([PgApprovalResponse.pgTransactionId]). */
    val pgTransactionId: String,
    val reason: String
)

data class PgCancelResponse(
    val canceled: Boolean,
    val provider: PgProvider,
    val failureReason: String? = null
)
