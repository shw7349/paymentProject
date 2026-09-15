package com.study.payment.presentation

import com.study.payment.domain.payment.DuplicatePaymentException
import com.study.payment.domain.payment.InvalidPaymentStateException
import com.study.payment.domain.payment.LockAcquisitionException
import com.study.payment.domain.payment.PaymentNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DuplicatePaymentException::class)
    fun handleDuplicate(e: DuplicatePaymentException): ResponseEntity<ErrorResponse> {
        log.info("Duplicate payment rejected: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("DUPLICATE_PAYMENT", e.message ?: ""))
    }

    @ExceptionHandler(PaymentNotFoundException::class)
    fun handleNotFound(e: PaymentNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("PAYMENT_NOT_FOUND", e.message ?: ""))

    @ExceptionHandler(LockAcquisitionException::class)
    fun handleLockTimeout(e: LockAcquisitionException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse("LOCK_TIMEOUT", e.message ?: ""))

    @ExceptionHandler(InvalidPaymentStateException::class)
    fun handleInvalidState(e: InvalidPaymentStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("INVALID_PAYMENT_STATE", e.message ?: ""))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("INVALID_ARGUMENT", e.message ?: ""))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse("VALIDATION_ERROR", message))
    }
}
