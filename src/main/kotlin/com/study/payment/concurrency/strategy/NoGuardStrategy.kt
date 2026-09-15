package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.ExternalCallSimulator
import com.study.payment.concurrency.StockDecrementResult
import com.study.payment.concurrency.StockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 방어 없음(none). 재고를 읽어(read) 메모리에서 1 줄이고(modify) 저장(write)한다.
 *
 * 두 트랜잭션이 같은 재고(예: 100)를 동시에 읽으면 둘 다 99 를 쓰므로 한 번의 차감이 사라진다
 * (lost update). 성공 응답 수보다 실제로 줄어든 재고가 적거나, 심하면 재고가 음수가 되는
 * oversell 이 발생한다. 임계 구역 안의 외부 호출 지연([ExternalCallSimulator])이 read 와 write
 * 사이 간격을 벌려 경쟁을 더 잘 드러낸다.
 */
@Component
class NoGuardStrategy(
    private val stockRepository: StockRepository,
    private val externalCall: ExternalCallSimulator
) : StockDecrementStrategy {

    override val name = "none"

    @Transactional
    override fun decrement(productId: String): StockDecrementResult {
        val stock = stockRepository.findById(productId).orElseThrow()
        if (stock.isSoldOut) return StockDecrementResult.SOLD_OUT

        externalCall.waitForExternalResponse()

        stock.decrease()
        stockRepository.saveAndFlush(stock)
        return StockDecrementResult.SUCCESS
    }
}
