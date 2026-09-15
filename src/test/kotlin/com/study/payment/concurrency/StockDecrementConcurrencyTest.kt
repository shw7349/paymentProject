package com.study.payment.concurrency

import com.study.payment.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 단일 인스턴스(하나의 JVM) 안에서 각 재고 차감 전략을 동시에 실행해 정합성을 비교한다.
 *
 * 임계 구역 안 외부 호출 지연(`concurrency.external-call-delay-millis=30`)이 read/write 사이 간격을
 * 벌려, '방어 없음' 전략의 lost update 를 안정적으로 재현한다.
 *
 * 주의: 이 테스트는 **단일 JVM** 실험이다. 그래서 `synchronized` 도 여기서는 정상 통과한다.
 * `synchronized` 가 뚫리는 건 인스턴스를 2개 이상 띄웠을 때뿐이며, 그 재현은 `scripts/concurrency-load.sh`
 * 로 두 포트에 동시에 쏘아 관찰한다(README 의 결과표 참고).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["concurrency.external-call-delay-millis=30"])
class StockDecrementConcurrencyTest {

    @Autowired
    private lateinit var service: StockConcurrencyService

    private val initialStock = 10
    private val concurrency = 30

    @Test
    fun `방어 없음 전략은 단일 JVM 에서도 lost update 로 정합성이 깨진다`() {
        val outcome = runExperiment("none")
        println(outcome.render())

        // 성공 응답 수보다 실제 차감량이 적다 = 갱신 손실(lost update)이 발생했다.
        assertTrue(
            outcome.actualDecreased < outcome.success,
            "방어가 없으면 성공 수(${outcome.success})보다 실제 차감량(${outcome.actualDecreased})이 적어야 한다(lost update)"
        )
    }

    @Test
    fun `synchronized 전략은 단일 JVM 에서 정확히 재고만큼만 성공한다`() =
        assertSingleInstanceCorrect("synchronized")

    @Test
    fun `SELECT FOR UPDATE 전략은 정확히 재고만큼만 성공한다`() =
        assertSingleInstanceCorrect("for-update")

    @Test
    fun `Redis SETNX 전략은 정확히 재고만큼만 성공한다`() =
        assertSingleInstanceCorrect("redis-setnx")

    @Test
    fun `조건부 UPDATE 전략은 정확히 재고만큼만 성공한다`() =
        assertSingleInstanceCorrect("conditional-update")

    private fun assertSingleInstanceCorrect(strategy: String) {
        val outcome = runExperiment(strategy)
        println(outcome.render())

        assertEquals(0, outcome.remaining, "$strategy: 재고가 정확히 0 이어야 한다(음수 = oversell)")
        assertEquals(initialStock, outcome.success, "$strategy: 성공은 초기 재고($initialStock) 만큼만 나와야 한다")
        assertEquals(initialStock, outcome.actualDecreased, "$strategy: 실제 차감량이 초기 재고와 같아야 한다(lost update 없음)")
        assertEquals(concurrency - initialStock, outcome.soldOut + outcome.lockFailed, "$strategy: 나머지는 재고소진/락실패로 거절돼야 한다")
    }

    private fun runExperiment(strategy: String): Outcome {
        val productId = "$strategy-${UUID.randomUUID()}"
        service.reset(productId, initialStock)

        val executor = Executors.newFixedThreadPool(concurrency)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(concurrency)
        val success = AtomicInteger(0)
        val soldOut = AtomicInteger(0)
        val lockFailed = AtomicInteger(0)

        repeat(concurrency) {
            executor.submit {
                try {
                    startLatch.await()
                    when (service.decrement(strategy, productId)) {
                        StockDecrementResult.SUCCESS -> success.incrementAndGet()
                        StockDecrementResult.SOLD_OUT -> soldOut.incrementAndGet()
                        StockDecrementResult.LOCK_FAILED -> lockFailed.incrementAndGet()
                    }
                } catch (_: Throwable) {
                    // 방어 없음 전략에서 드물게 나는 예외는 결과표엔 반영하지 않는다(정합성 판단은 재고로 한다).
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(60, TimeUnit.SECONDS)
        executor.shutdown()

        val remaining = service.currentQuantity(productId)
        return Outcome(strategy, initialStock, success.get(), soldOut.get(), lockFailed.get(), remaining)
    }

    private data class Outcome(
        val strategy: String,
        val initial: Int,
        val success: Int,
        val soldOut: Int,
        val lockFailed: Int,
        val remaining: Int
    ) {
        val actualDecreased: Int get() = initial - remaining

        fun render(): String = buildString {
            appendLine("──────────────────────────────────────────────")
            appendLine("전략: $strategy")
            appendLine("  초기재고=$initial 성공=$success 재고소진=$soldOut 락실패=$lockFailed")
            appendLine("  남은재고=$remaining 실제차감=$actualDecreased")
            val verdict = when {
                remaining < 0 -> "❌ OVERSELL(재고 음수)"
                actualDecreased != success -> "❌ LOST UPDATE(성공수≠실제차감)"
                else -> "✅ 정합성 OK"
            }
            append("  판정: $verdict")
        }
    }
}
