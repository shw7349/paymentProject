package com.study.payment.presentation

import com.study.payment.concurrency.StockConcurrencyService
import com.study.payment.concurrency.StockDecrementResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 동시성 실험 진입점. 부하 스크립트나 2개 이상의 인스턴스가 같은 상품에 동시에 요청을 쏘도록
 * 열어 둔 HTTP API 다.
 *
 *  - `POST /api/concurrency/reset?productId=..&quantity=..`  실험 전 재고 초기화
 *  - `POST /api/concurrency/{strategy}/decrement?productId=..` 지정 전략으로 재고 1 차감
 *  - `GET  /api/concurrency/stock?productId=..`               남은 재고 조회
 *  - `GET  /api/concurrency/strategies`                       사용 가능한 전략 목록
 */
@RestController
@RequestMapping("/api/concurrency")
class ConcurrencyController(
    private val service: StockConcurrencyService
) {

    @PostMapping("/reset")
    fun reset(
        @RequestParam productId: String,
        @RequestParam quantity: Int
    ): ResponseEntity<Map<String, Any>> {
        service.reset(productId, quantity)
        return ResponseEntity.ok(mapOf("productId" to productId, "quantity" to quantity))
    }

    @PostMapping("/{strategy}/decrement")
    fun decrement(
        @PathVariable strategy: String,
        @RequestParam productId: String
    ): ResponseEntity<Map<String, Any>> {
        val result = service.decrement(strategy, productId)
        val status = when (result) {
            StockDecrementResult.SUCCESS -> HttpStatus.OK
            StockDecrementResult.SOLD_OUT -> HttpStatus.CONFLICT
            StockDecrementResult.LOCK_FAILED -> HttpStatus.TOO_MANY_REQUESTS
        }
        return ResponseEntity.status(status).body(mapOf("strategy" to strategy, "result" to result.name))
    }

    @GetMapping("/stock")
    fun stock(@RequestParam productId: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("productId" to productId, "quantity" to service.currentQuantity(productId)))

    @GetMapping("/strategies")
    fun strategies(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("strategies" to service.strategyNames))
}
