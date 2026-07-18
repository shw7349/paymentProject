package com.study.payment

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<PaymentProjectApplication>().with(TestcontainersConfiguration::class).run(*args)
}
