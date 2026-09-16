package com.study.payment.infrastructure.pg.vendor

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

/**
 * 가상 PG "OrbitPay" 의 벤더 SDK 를 흉내 낸 목업.
 *
 * OrbitPay 는 **예외 방식**이다: 성공하면 영수증([OrbitPayReceipt])을 반환하지만, 거절되면
 * [OrbitPayException] 을 던진다. 정상 흐름과 실패 흐름이 리턴값/예외로 갈리는 형태여서, 어댑터는
 * try/catch 로 이를 잡아 공통 모델의 성공/실패로 정규화한다.
 */
@Component
class OrbitPayApi {
    fun charge(request: OrbitPayCharge): OrbitPayReceipt {
        Thread.sleep(Random.nextLong(50L, 300L))
        if (Random.nextInt(100) < 10) {
            throw OrbitPayException(code = "E_DECLINED", detail = "card issuer declined")
        }
        return OrbitPayReceipt(receiptId = "ORBIT-${UUID.randomUUID()}")
    }

    fun voidCharge(receiptId: String): OrbitPayReceipt {
        Thread.sleep(Random.nextLong(50L, 200L))
        return OrbitPayReceipt(receiptId = receiptId)
    }
}

data class OrbitPayCharge(
    val referenceId: String,
    val value: BigDecimal,
    val currencyUnit: String
)

/** OrbitPay 성공 응답: 영수증. 실패는 값이 아니라 예외로 표현된다. */
data class OrbitPayReceipt(
    val receiptId: String
)

class OrbitPayException(
    val code: String,
    val detail: String
) : RuntimeException("OrbitPay declined [$code]: $detail")
