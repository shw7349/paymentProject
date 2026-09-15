package com.study.payment.concurrency.strategy

import com.study.payment.concurrency.ExternalCallSimulator
import com.study.payment.concurrency.StockDecrementResult
import com.study.payment.concurrency.StockRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID

/**
 * Redis `SET key value NX PX ttl` 기반 분산 락.
 *
 * JVM `synchronized` 와 달리 락이 프로세스 밖(Redis)에 있으므로 **다중 인스턴스에서도** 같은 상품에
 * 대한 임계 구역을 하나로 직렬화해 oversell 을 막는다. `synchronized` 가 다중 인스턴스에서 뚫리는
 * 문제를 해결하는 대표적인 방법이다.
 *
 * 교육 목적의 최소 구현이라 주의할 점이 있다:
 *  - 락은 TTL([leaseTime])로 만료된다. 임계 구역이 TTL 보다 길어지면 락이 먼저 풀려 다른 요청이
 *    끼어들 수 있다(그래서 DB 유니크/조건부 UPDATE 같은 최종 방어선이 여전히 필요하다).
 *  - 해제는 자신이 건 락(토큰 일치)만 지우도록 Lua 로 compare-and-delete 한다.
 *  - 운영에서는 이 모든 걸 검증된 Redisson 락([com.study.payment.infrastructure.lock.DistributedLockExecutor])
 *    으로 대체하는 편이 낫다. 여기서는 SETNX 의 동작 원리를 드러내려고 직접 구현했다.
 */
@Component
class RedisSetNxStrategy(
    private val stockRepository: StockRepository,
    private val externalCall: ExternalCallSimulator,
    private val transactionTemplate: TransactionTemplate,
    private val redisTemplate: StringRedisTemplate
) : StockDecrementStrategy {

    override val name = "redis-setnx"

    private val waitTime = Duration.ofSeconds(5)
    private val leaseTime = Duration.ofSeconds(3)
    private val retryInterval = Duration.ofMillis(50)

    private val releaseScript = DefaultRedisScript(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long::class.java
    )

    override fun decrement(productId: String): StockDecrementResult {
        val key = "lock:stock:$productId"
        val token = UUID.randomUUID().toString()

        if (!acquire(key, token)) return StockDecrementResult.LOCK_FAILED
        try {
            return transactionTemplate.execute {
                val stock = stockRepository.findById(productId).orElseThrow()
                if (stock.isSoldOut) return@execute StockDecrementResult.SOLD_OUT

                externalCall.waitForExternalResponse()

                stock.decrease()
                stockRepository.saveAndFlush(stock)
                StockDecrementResult.SUCCESS
            }!!
        } finally {
            release(key, token)
        }
    }

    /** SET NX PX 로 락 획득을 시도하고, 실패하면 waitTime 안에서 주기적으로 재시도한다. */
    private fun acquire(key: String, token: String): Boolean {
        val deadline = System.nanoTime() + waitTime.toNanos()
        while (System.nanoTime() < deadline) {
            val ok = redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime)
            if (ok == true) return true
            Thread.sleep(retryInterval.toMillis())
        }
        return false
    }

    /** 자신이 건 락(토큰 일치)일 때만 지운다. */
    private fun release(key: String, token: String) {
        redisTemplate.execute(releaseScript, listOf(key), token)
    }
}
