package com.study.payment.infrastructure.pg.vendor

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

/**
 * 가상 PG "NovaPay" 의 벤더 SDK 를 흉내 낸 목업.
 *
 * NovaPay 는 **결과 코드 방식**이다: 성공/실패를 `resultCode` 문자열("0000"=성공)로 돌려준다.
 * 이 벤더 고유의 형태를 [com.study.payment.infrastructure.pg.adapter.NovaPayGateway] 가 공통 모델로 바꾼다.
 */
@Component
class NovaPayApi {
    fun requestApproval(request: NovaPayRequest): NovaPayResponse {
        Thread.sleep(Random.nextLong(50L, 300L))
        return if (Random.nextInt(100) >= 10) {
            NovaPayResponse(resultCode = "0000", tid = "NOVA-${UUID.randomUUID()}")
        } else {
            NovaPayResponse(resultCode = "0110", tid = null) // 0110 = 한도 초과(가상)
        }
    }

    fun cancelApproval(tid: String): NovaPayResponse {
        Thread.sleep(Random.nextLong(50L, 200L))
        return NovaPayResponse(resultCode = "0000", tid = tid)
    }
}

data class NovaPayRequest(
    val merchantOrderId: String,
    val amount: BigDecimal,
    val currencyCode: String
)

/** NovaPay 응답: 결과는 코드로만 온다. */
data class NovaPayResponse(
    val resultCode: String,
    val tid: String?
)
