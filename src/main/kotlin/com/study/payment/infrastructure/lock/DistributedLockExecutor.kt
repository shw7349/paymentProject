package com.study.payment.infrastructure.lock

import com.study.payment.domain.payment.LockAcquisitionException
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Guards a critical section with a Redis (Redisson) distributed lock keyed by [key].
 *
 * This is the *first* line of defense against concurrent duplicate requests (e.g. a user
 * double-clicking "pay", or a client retrying a request that is still in flight). It is not
 * the only defense: the `order_id` column on `payments` also carries a DB unique constraint,
 * which is what actually guarantees correctness if the lock is ever bypassed (lease expiry
 * under a very slow action, a Redis failover losing the lock, a bug, etc).
 */
@Component
class DistributedLockExecutor(
    private val redissonClient: RedissonClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> executeWithLock(
        key: String,
        waitTime: Duration = Duration.ofSeconds(5),
        leaseTime: Duration = Duration.ofSeconds(3),
        action: () -> T
    ): T {
        val lock = redissonClient.getLock(key)
        val acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            log.warn("Failed to acquire lock for key={} within waitTime={}", key, waitTime)
            throw LockAcquisitionException(key)
        }
        log.debug("Acquired lock for key={}", key)
        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
                log.debug("Released lock for key={}", key)
            }
        }
    }

    companion object {
        fun paymentLockKey(orderId: String): String = "lock:payment:$orderId"
    }
}
