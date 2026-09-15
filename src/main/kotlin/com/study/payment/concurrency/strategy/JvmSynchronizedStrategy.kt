package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.ExternalCallSimulator
import com.study.payment.concurrency.StockDecrementResult
import com.study.payment.concurrency.StockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * JVM `synchronized`. 재고 읽기-차감-저장을 프로세스 내 모니터 락으로 직렬화한다.
 *
 * 단일 인스턴스에서는 lost update 를 막지만, 락이 **JVM 프로세스 안에서만** 유효하다는 한계가 있다.
 * 애플리케이션을 2개 이상 띄우면 각 프로세스가 서로 다른 모니터를 잡으므로 두 인스턴스가 동시에
 * 임계 구역에 진입해 다시 oversell 이 발생한다. 이것이 이 실험의 핵심 교훈이다.
 *
 * `synchronized` 블록 안에서 트랜잭션을 시작해야 "락 → 트랜잭션 → 커밋 → 락 해제" 순서가 지켜진다.
 * (`@Transactional` 메서드에 `synchronized` 를 붙이면 커밋이 락 해제 뒤에 일어나 여전히 샌다.)
 */
@Component
class JvmSynchronizedStrategy(
    private val stockRepository: StockRepository,
    private val externalCall: ExternalCallSimulator,
    private val transactionTemplate: TransactionTemplate
) : StockDecrementStrategy {

    override val name = "synchronized"

    private val monitor = Any()

    override fun decrement(productId: String): StockDecrementResult {
        synchronized(monitor) {
            return transactionTemplate.execute {
                val stock = stockRepository.findById(productId).orElseThrow()
                if (stock.isSoldOut) return@execute StockDecrementResult.SOLD_OUT

                externalCall.waitForExternalResponse()

                stock.decrease()
                stockRepository.saveAndFlush(stock)
                StockDecrementResult.SUCCESS
            }!!
        }
    }
}
