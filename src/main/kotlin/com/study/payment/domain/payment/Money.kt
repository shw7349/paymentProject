package com.study.payment.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

@Embeddable
data class Money(
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "KRW"
) {
    init {
        require(amount > BigDecimal.ZERO) { "amount must be positive: $amount" }
        require(currency.length == 3) { "currency must be an ISO-4217 3-letter code: $currency" }
    }
}
