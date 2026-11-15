package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class BankPushParserTest {
    @Test fun parsesTwoPurchasesAndExcludesOtherAmounts() {
        val purchases = BankPushParser.parse(listOf(
            "Пятёрочка Покупка на 610,96 ₽, кешбэк 6 ₽, счёт карты *2017",
            "Фастфуд Покупка на 500 ₽, кешбэк 5 ₽, счёт карты *2017"
        ))
        assertEquals(listOf("Пятёрочка", "Фастфуд"), purchases.map { it.merchant })
        assertEquals(listOf(61096L, 50000L), purchases.map { it.amountMinor })
    }
    @Test fun refusesUnknownAndNonPurchases() {
        assertTrue(BankPushParser.parse(listOf(
            "Кешбэк 10 ₽", "Перевод на 3 000 ₽", "Возврат Покупка на 2 500 ₽",
            "Пятёрочка Покупка на 5 €", "Покупка на 90 ₽" // No merchant
        )).isEmpty())
    }
    @Test fun acceptsGroupSeparatorsButDoesNotDuplicateLines() {
        val rows = BankPushParser.parse(listOf(
            "Магнит Покупка на 1 234,56 ₽, кешбэк 12 ₽",
            "Магнит Покупка на 1 234,56 ₽, кешбэк 12 ₽"
        ))
        assertEquals(1,rows.size)
        assertEquals(123456L,rows.single().amountMinor)
    }
    @Test fun windowSuppressesRepostWithoutSuppressingLaterGenuinePurchase() {
        assertTrue(BankImportRules.duplicate(listOf(1_000L),1_200L))
        assertFalse(BankImportRules.duplicate(listOf(1_000L),2_001L))
    }
}
