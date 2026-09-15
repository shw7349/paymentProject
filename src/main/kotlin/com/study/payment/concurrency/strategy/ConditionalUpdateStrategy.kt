package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.ExternalCallSimulator
import com.study.payment.concurrency.StockDecrementResult
import com.study.payment.concurrency.StockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 조건부 UPDATE. `UPDATE stocks SET quantity = quantity - 1 WHERE product_id = ? AND quantity > 0`.
 *
 * 재고 확인과 차감이 DB 엔진 안에서 원자적으로 일어나므로, 애플리케이션 락 없이도 다중 인스턴스에서
 * oversell 이 발생하지 않는다. 영향받은 행 수가 1 이면 성공, 0 이면 재고 소진이다. 행 락을 오래
 * 붙잡지 않아(문장 실행 순간에만 잠금) `SELECT FOR UPDATE` 대비 커넥션 점유 시간이 짧다.
 *
 * 외부 호출(PG 승인 등)은 차감이 확정된 **뒤**, 트랜잭션/락 밖에서 수행하는 것이 좋다. 여기서는
 * 그 순서를 보여주기 위해 UPDATE 커밋 이후에 외부 호출 지연을 흉내 낸다.
 */
@Component
class ConditionalUpdateStrategy(
    private val stockRepository: StockRepository,
    private val externalCall: ExternalCallSimulator,
    private val transactionTemplate: TransactionTemplate
) : StockDecrementStrategy {

    override val name = "conditional-update"

    override fun decrement(productId: String): StockDecrementResult {
        val affected = transactionTemplate.execute { stockRepository.decrementIfAvailable(productId) }!!
        if (affected == 0) return StockDecrementResult.SOLD_OUT

        // 재고는 이미 확정 차감됐고 락도 풀린 상태에서 외부 호출을 한다(커넥션을 붙잡지 않음).
        externalCall.waitForExternalResponse()
        return StockDecrementResult.SUCCESS
    }
}
