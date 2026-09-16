package com.study.payment.infrastructure.pg

import com.study.payment.domain.payment.PaymentMethod
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 결제수단/정책에 따라 적절한 [PaymentGateway] 어댑터를 골라 위임하는 공통 진입점(전략 선택기).
 *
 * 스프링이 주입한 모든 `PaymentGateway` 빈을 provider 키로 모아 두므로, PG 어댑터를 추가하기만 하면
 * 여기 코드를 고치지 않아도 자동으로 후보에 포함된다. 결제 흐름(Kafka 리스너)은 오직 이 라우터에만
 * 의존하고 개별 PG 의 존재를 모른다.
 */
@Component
class PaymentGatewayRouter(
    gateways: List<PaymentGateway>,
    private val routingPolicy: PgRoutingPolicy
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val gatewaysByProvider: Map<PgProvider, PaymentGateway> = gateways.associateBy { it.provider }

    init {
        log.info("Registered PG adapters: {}", gatewaysByProvider.keys)
    }

    /** 결제수단에 맞는 PG 를 골라 승인을 위임한다. */
    fun approve(command: PgApprovalCommand): PgApprovalResponse {
        val gateway = resolve(command.paymentMethod)
        log.info("Routing approval orderId={} method={} -> {}", command.orderId, command.paymentMethod, gateway.provider)
        return gateway.approve(command)
    }

    /** 원 승인을 처리했던 PG 로 취소(보상 트랜잭션)를 위임한다. */
    fun cancel(provider: PgProvider, command: PgCancelCommand): PgCancelResponse =
        gatewayOf(provider).cancel(command)

    private fun resolve(method: PaymentMethod): PaymentGateway {
        val provider = routingPolicy.providerFor(method)
        val gateway = gatewayOf(provider)
        require(gateway.supports(method)) {
            "PG $provider does not support paymentMethod=$method (routing policy misconfigured)"
        }
        return gateway
    }

    private fun gatewayOf(provider: PgProvider): PaymentGateway =
        gatewaysByProvider[provider]
            ?: throw IllegalStateException("no PaymentGateway adapter registered for provider=$provider")
}
