package com.study.payment.infrastructure.pg.vendor

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

/**
 * 가상 PG "LunaPay" 의 벤더 SDK 를 흉내 낸 목업.
 *
 * LunaPay 는 **상태 문자열 방식**이다: `status`("APPROVED"/"DECLINED")와 사람이 읽는 `message` 를
 * 돌려준다. NovaPay 의 코드 방식과 형태가 완전히 다르며, 이를 흡수하는 것이 어댑터의 역할이다.
 */
@Component
class LunaPayApi {
    fun pay(request: LunaPayRequest): LunaPayResult {
        Thread.sleep(Random.nextLong(50L, 300L))
        return if (Random.nextInt(100) >= 10) {
            LunaPayResult(status = "APPROVED", transactionKey = "LUNA-${UUID.randomUUID()}", message = "ok")
        } else {
            LunaPayResult(status = "DECLINED", transactionKey = null, message = "insufficient balance")
        }
    }

    fun refund(transactionKey: String): LunaPayResult {
        Thread.sleep(Random.nextLong(50L, 200L))
        return LunaPayResult(status = "APPROVED", transactionKey = transactionKey, message = "refunded")
    }
}

data class LunaPayRequest(
    val orderNo: String,
    val payAmount: BigDecimal,
    val currency: String
)

/** LunaPay 응답: 결과는 상태 문자열로 온다. */
data class LunaPayResult(
    val status: String,
    val transactionKey: String?,
    val message: String
)
