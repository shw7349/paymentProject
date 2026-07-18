package com.study.payment.domain.payment

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `rejects zero or negative amounts`() {
        assertFailsWith<IllegalArgumentException> { Money(BigDecimal.ZERO, "KRW") }
        assertFailsWith<IllegalArgumentException> { Money(BigDecimal(-1), "KRW") }
    }

    @Test
    fun `rejects a currency code that is not 3 letters`() {
        assertFailsWith<IllegalArgumentException> { Money(BigDecimal(1000), "WONS") }
        assertFailsWith<IllegalArgumentException> { Money(BigDecimal(1000), "KR") }
    }
}
