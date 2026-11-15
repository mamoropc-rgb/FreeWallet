package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class BankSubaccountsTest {
    private val primary = Account(1, "Основная карта", "RUB", 200_000L, 200_000L, 1_000_000L,
        bankGroup = "Т-Банк", cardLast4 = "2017")
    private val savings = Account(2, "Накопительный", "RUB", 750_000L, 750_000L, 1_000_000L,
        bankGroup = "Т-Банк", cardLast4 = "8002")

    @Test fun exactCardSuffixRoutesToCorrectSubaccount() {
        val all = listOf(primary, savings)
        assertEquals(primary.id, BankSubaccounts.routeTBankPurchase(all,BankPurchase("Магнит",15000,"2017"), 2)?.id)
        assertEquals(savings.id, BankSubaccounts.routeTBankPurchase(all,BankPurchase("Аптека",2700,"8002"), 1)?.id)
    }

    @Test fun ambiguousOrWrongSuffixNeverMakesUpAnAccount() {
        val all = listOf(primary, savings)
        assertNull(BankSubaccounts.routeTBankPurchase(all,BankPurchase("Магнит",15000), 1))
        assertNull(BankSubaccounts.routeTBankPurchase(all,BankPurchase("Магнит",15000,"9999"), 1))
        assertEquals(primary.id,BankSubaccounts.routeTBankPurchase(listOf(primary),BankPurchase("Магнит",15000),2)?.id)
    }

    @Test fun groupingNeverChangesCashBalanceOrDuplicatesBankTotal() {
        val s=Snapshot(accounts=listOf(primary, savings))
        assertEquals(950_000L,LedgerMath.cashBase(s))
        assertEquals(950_000L,s.accounts.sumOf { LedgerMath.accountBase(it,s.entries) })
    }

    @Test fun bankCardMatchesTwoSubaccountsExample() {
        val a=Account(10,"Основной остаток","BYN",1969L,54053L,27451800L,bankGroup="БеларусьБанк")
        val b=Account(11,"2018","BYN",5000L,137259L,27451800L,bankGroup="БеларусьБанк",cardLast4="2018")
        assertEquals(6969L,BankSubaccounts.totalNative(listOf(a,b),emptyList()))
        val between=Entry(1,"2026-11-15",Kind.TRANSFER,10,500L,13726L,"Перевод",toAccount=11,toAmountMinor=500L)
        assertEquals(6969L,BankSubaccounts.totalNative(listOf(a,b),listOf(between)))
        assertThrows(IllegalArgumentException::class.java) {
            BankSubaccounts.totalNative(listOf(a,primary),emptyList())
        }
    }

    @Test fun duplicateCardSuffixInSameBankIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BankSubaccounts.verifyMetadata(listOf(primary),"Тинькофф","2017")
        }
        BankSubaccounts.verifyMetadata(listOf(primary),"Другой банк","2017")
    }

    @Test fun cleanBankCombinesKnownAliases() {
        assertEquals("Т-Банк",BankSubaccounts.cleanBank("  Тинькофф   "))
        assertTrue(BankSubaccounts.isTBank("T-Bank"))
        assertEquals("Сбер",BankSubaccounts.cleanBank(" Сбер  "))
    }
}
