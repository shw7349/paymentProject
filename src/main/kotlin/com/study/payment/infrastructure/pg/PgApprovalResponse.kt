package com.study.payment.infrastructure.pg

/**
 * PG 승인 결과의 공통 출력 모델. 각 PG 의 서로 다른 응답(코드/문자열/예외 등)을 어댑터가 이 하나의
 * 형태로 정규화하므로, 공통 로직은 어떤 PG 를 썼는지 몰라도 성공/실패만 보고 처리하면 된다.
 */
data class PgApprovalResponse(
    val approved: Boolean,
    val provider: PgProvider,
    /** PG 가 발급한 승인 거래 식별자(성공 시). 취소/보상 트랜잭션에서 원거래를 지목하는 데 쓴다. */
    val pgTransactionId: String? = null,
    /** 실패 시 PG 별 원본 코드(예: "0110"). 우리 도메인 코드가 아니라 진단용이다. */
    val failureCode: String? = null,
    val failureReason: String? = null
) {
    companion object {
        fun approved(provider: PgProvider, pgTransactionId: String): PgApprovalResponse =
            PgApprovalResponse(approved = true, provider = provider, pgTransactionId = pgTransactionId)

        fun declined(provider: PgProvider, failureCode: String, failureReason: String): PgApprovalResponse =
            PgApprovalResponse(
                approved = false,
                provider = provider,
                failureCode = failureCode,
                failureReason = failureReason
            )
    }
}
