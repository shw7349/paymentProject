package com.study.payment.concurrency

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 임계 구역 안에서 일어나는 외부 호출(예: PG 승인 요청)의 지연을 흉내 낸다.
 *
 * `SELECT ... FOR UPDATE` 로 행/커넥션을 잡은 채 이 지연을 기다리면, 그 시간만큼 DB 커넥션이
 * 점유된다. 지연을 크게 주고 동시 요청을 많이 쏘면 커넥션 풀이 고갈되며 처리량이 무너지는 모습을
 * 재현할 수 있다.
 *
 * `concurrency.external-call-delay-millis` 설정으로 지연을 조절한다(기본 0 = 지연 없음).
 */
@Component
class ExternalCallSimulator(
    @Value("\${concurrency.external-call-delay-millis:0}") private val delayMillis: Long
) {
    /** 설정된 지연만큼(밀리초) 현재 스레드를 멈춰 외부 응답 대기를 흉내 낸다. */
    fun waitForExternalResponse() {
        if (delayMillis > 0) {
            Thread.sleep(delayMillis)
        }
    }
}
