package com.study.payment.infrastructure.pg

/**
 * 결제 게이트웨이(PG) 사업자 식별자.
 *
 * 실제 PG 가 아니라 스터디용 가명이다. 각 값에 대응하는 어댑터([com.study.payment.infrastructure.pg.adapter])가
 * 자신만의 벤더 API 응답을 공통 모델([PgApprovalResponse])로 변환한다.
 * PG 를 추가하려면 이 enum 에 값을 더하고 어댑터 하나만 구현하면 되며, 공통 로직(라우터/리스너)은
 * 건드리지 않는다.
 */
enum class PgProvider {
    NOVA_PAY,
    LUNA_PAY,
    ORBIT_PAY
}
