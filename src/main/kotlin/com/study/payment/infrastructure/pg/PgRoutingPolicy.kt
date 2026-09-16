package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.PaymentMethod
import org.springframework.stereotype.Component

/**
 * 결제수단별로 어느 PG 를 우선 사용할지 정하는 라우팅 규칙.
 *
 * "결제수단이 추가돼도 공통 로직을 건드리지 않는다"의 핵심 확장점이다. 새 결제수단이나 PG 정책은
 * 이 매핑만 바꾸면 되고, 라우터/어댑터/리스너 코드는 그대로다. 실무라면 이 매핑을 설정(yml)이나
 * DB 에서 읽어 무중단으로 바꾸겠지만, 스터디에서는 코드 상수로 둔다.
 */
@Component
class PgRoutingPolicy {
    private val providerByMethod: Map<PaymentMethod, PgProvider> = mapOf(
        PaymentMethod.CARD to PgProvider.NOVA_PAY,
        PaymentMethod.VIRTUAL_ACCOUNT to PgProvider.LUNA_PAY
    )

    fun providerFor(method: PaymentMethod): PgProvider =
        providerByMethod[method]
            ?: throw IllegalArgumentException("no PG routing configured for paymentMethod=$method")
}
