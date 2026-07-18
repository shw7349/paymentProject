package com.study.payment.domain.payment

sealed class PaymentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class InvalidPaymentStateException(
    status: PaymentStatus,
    action: String
) : PaymentException("Cannot $action payment in status $status")

class DuplicatePaymentException(
    orderId: String,
    cause: Throwable? = null
) : PaymentException("Payment for orderId=$orderId already exists", cause)

class PaymentNotFoundException(
    identifier: String
) : PaymentException("Payment not found: $identifier")

class LockAcquisitionException(
    key: String
) : PaymentException("Failed to acquire lock for key=$key")
