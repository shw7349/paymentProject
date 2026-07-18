package com.study.payment.presentation

import com.study.payment.domain.payment.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class PaymentRequestDto(
    @field:NotBlank
    val orderId: String,

    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,

    @field:NotBlank
    @field:Size(min = 3, max = 3)
    val currency: String,

    @field:NotNull
    val paymentMethod: PaymentMethod
)
