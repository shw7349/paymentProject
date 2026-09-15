package com.study.payment.concurrency

import com.study.payment.concurrency.strategy.StockDecrementStrategy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전략 레지스트리 + 실험 준비/조회. 컨트롤러와 부하 스크립트가 전략 이름으로 재고 차감을 호출하고,
 * 실험 시작 전 재고를 초기화하며, 끝난 뒤 남은 재고를 확인하는 진입점이다.
 */
@Service
class StockConcurrencyService(
    strategies: List<StockDecrementStrategy>,
    private val stockRepository: StockRepository
) {
    private val strategiesByName: Map<String, StockDecrementStrategy> = strategies.associateBy { it.name }

    val strategyNames: List<String> get() = strategiesByName.keys.sorted()

    fun decrement(strategyName: String, productId: String): StockDecrementResult {
        val strategy = strategiesByName[strategyName]
            ?: throw IllegalArgumentException("unknown strategy '$strategyName'; available=$strategyNames")
        return strategy.decrement(productId)
    }

    /** 실험 시작 전 재고를 주어진 수량으로 (없으면 생성, 있으면 덮어써서) 초기화한다. */
    @Transactional
    fun reset(productId: String, quantity: Int) {
        val stock = stockRepository.findById(productId).orElse(null)
        if (stock == null) {
            stockRepository.save(Stock(productId, quantity))
        } else {
            stock.quantity = quantity
        }
    }

    @Transactional(readOnly = true)
    fun currentQuantity(productId: String): Int =
        stockRepository.findById(productId).orElseThrow().quantity
}
