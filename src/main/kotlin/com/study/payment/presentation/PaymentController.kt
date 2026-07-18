package com.study.payment.presentation

import com.study.payment.application.PaymentApplicationService
import com.study.payment.application.RequestPaymentCommand
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentApplicationService: PaymentApplicationService
) {

    /** Returns 202 Accepted: the payment is recorded as REQUESTED and approved asynchronously via Kafka. */
    @PostMapping
    fun requestPayment(@Valid @RequestBody request: PaymentRequestDto): ResponseEntity<PaymentResponseDto> {
        val result = paymentApplicationService.requestPayment(
            RequestPaymentCommand(
                orderId = request.orderId,
                amount = request.amount,
                currency = request.currency,
                paymentMethod = request.paymentMethod
            )
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PaymentResponseDto.from(result))
    }

    @GetMapping("/{orderId}")
    fun getPayment(@PathVariable orderId: String): ResponseEntity<PaymentResponseDto> {
        val result = paymentApplicationService.getPayment(orderId)
        return ResponseEntity.ok(PaymentResponseDto.from(result))
    }
}
