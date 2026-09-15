package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.StockDecrementResult

/**
 * 재고 1 차감을 서로 다른 동시성 제어 방식으로 구현하기 위한 전략 인터페이스.
 *
 * 구현체:
 *  - [NoGuardStrategy]          방어 없음 (read-modify-write, lost update 발생)
 *  - [JvmSynchronizedStrategy]  JVM `synchronized` (단일 인스턴스에서만 유효)
 *  - [PessimisticLockStrategy]  `SELECT ... FOR UPDATE` (정확하나 커넥션을 점유)
 *  - [RedisSetNxStrategy]       Redis `SET NX` 분산 락 (다중 인스턴스에서 유효)
 *  - [ConditionalUpdateStrategy] 조건부 `UPDATE ... WHERE quantity > 0` (락 없이 원자적)
 *
 * 각 구현체의 [name] 은 REST 경로(`/api/concurrency/{strategy}/decrement`)와 결과표 라벨로 쓰인다.
 */
interface StockDecrementStrategy {
    val name: String

    fun decrement(productId: String): StockDecrementResult
}
