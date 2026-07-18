package com.study.payment.application

import com.study.payment.domain.payment.PaymentMethod
import java.math.BigDecimal

data class RequestPaymentCommand(
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethod
)
