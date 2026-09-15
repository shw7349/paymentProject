package com.study.payment.concurrency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 동시성 실험용 재고 애그리거트.
 *
 * 결제 도메인의 [com.study.payment.domain.payment.Payment] 와 달리 일부러 `@Version`(낙관적 락)을
 * 두지 않았다. '방어 없음' 전략이 아무런 안전장치 없이 lost update / oversell 을 일으키는 모습을
 * 그대로 관찰하기 위해서다. 방어는 오직 각 전략(synchronized, SELECT FOR UPDATE, Redis SETNX,
 * 조건부 UPDATE)이 명시적으로 제공하는 것만 존재한다.
 */
@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "quantity", nullable = false)
    var quantity: Int
) {
    val isSoldOut: Boolean get() = quantity <= 0

    /** 재고를 1 차감한다. 재고가 없으면 false 를 반환하고 아무것도 바꾸지 않는다. */
    fun decrease(): Boolean {
        if (isSoldOut) return false
        quantity -= 1
        return true
    }
}
