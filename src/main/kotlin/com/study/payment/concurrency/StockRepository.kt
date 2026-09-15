package com.study.payment.concurrency

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockRepository : JpaRepository<Stock, String> {

    /**
     * 비관적 쓰기 락(`SELECT ... FOR UPDATE`). 트랜잭션이 끝날 때까지 해당 행에 다른 트랜잭션이
     * 접근하지 못하도록 DB 가 잠근다. 이 조회로 얻은 커넥션은 트랜잭션이 커밋/롤백될 때까지
     * 점유되며, 임계 구역 안에서 외부 호출(예: PG 승인)을 기다리면 그 시간만큼 커넥션이 붙잡힌다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId = :productId")
    fun findByIdForUpdate(@Param("productId") productId: String): Stock?

    /**
     * 조건부 UPDATE. `quantity > 0` 인 경우에만 1 을 차감하며, 이 판단과 차감이 DB 엔진 안에서
     * 원자적으로 일어난다. 애플리케이션 락 없이도 oversell 이 발생하지 않고, 영향받은 행 수로
     * 성공/재고소진을 구분한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Stock s set s.quantity = s.quantity - 1 where s.productId = :productId and s.quantity > 0")
    fun decrementIfAvailable(@Param("productId") productId: String): Int
}
