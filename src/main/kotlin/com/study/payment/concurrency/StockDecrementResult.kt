package com.study.payment.concurrency

/** 한 번의 재고 차감 시도 결과. */
enum class StockDecrementResult {
    /** 실제로 1 차감에 성공. */
    SUCCESS,

    /** 재고가 이미 0 이라 차감하지 못함(정상적인 거절). */
    SOLD_OUT,

    /** 락 획득에 실패해 차감을 포기함(Redis SETNX 등에서 발생). */
    LOCK_FAILED
}
