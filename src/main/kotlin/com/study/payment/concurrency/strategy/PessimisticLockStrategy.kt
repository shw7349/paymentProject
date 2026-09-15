package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.ExternalCallSimulator
import com.study.payment.concurrency.StockDecrementResult
import com.study.payment.concurrency.StockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * `SELECT ... FOR UPDATE`(비관적 쓰기 락). 재고 행을 DB 락으로 잡은 채 차감한다.
 *
 * 다중 인스턴스에서도 DB 가 단일 진실 공급원이므로 oversell 을 정확히 막는다. 대신 **행 락을 잡은
 * 커넥션은 트랜잭션이 끝날 때까지 반납되지 않는다.** 임계 구역 안에서 외부 호출
 * ([ExternalCallSimulator]) 을 기다리면 그 시간만큼 커넥션이 붙잡히고, 같은 상품에 대한 요청은
 * 전부 직렬화되므로 지연 × 동시요청 수만큼 처리량이 무너지고 커넥션 풀이 고갈될 수 있다.
 *
 * `concurrency.external-call-delay-millis` 를 크게 주고(예: 500) 동시 요청을 쏘면 이 현상을
 * 재현할 수 있다.
 */
@Component
class PessimisticLockStrategy(
    private val stockRepository: StockRepository,
    private val externalCall: ExternalCallSimulator
) : StockDecrementStrategy {

    override val name = "for-update"

    @Transactional
    override fun decrement(productId: String): StockDecrementResult {
        // 이 시점에 행 락이 걸리고, 커넥션은 아래 외부 호출 대기 동안 계속 점유된다.
        val stock = stockRepository.findByIdForUpdate(productId) ?: error("stock not found: $productId")
        if (stock.isSoldOut) return StockDecrementResult.SOLD_OUT

        externalCall.waitForExternalResponse()

        stock.decrease()
        stockRepository.saveAndFlush(stock)
        return StockDecrementResult.SUCCESS
    }
}
