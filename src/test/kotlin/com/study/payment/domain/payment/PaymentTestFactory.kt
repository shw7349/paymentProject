package com.study.payment.domain.payment

/**
 * [Payment]'s constructor is private so production code is forced through the `request(...)`
 * factory. Tests that need to simulate an *already persisted* payment (i.e. one with a
 * generated id) reach past that via reflection instead of loosening the constructor's
 * visibility just for testability.
 */
object PaymentTestFactory {
    fun persisted(payment: Payment, id: Long): Payment {
        val field = Payment::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(payment, id)
        return payment
    }
}
