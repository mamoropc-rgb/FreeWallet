package com.mamoro.karman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class UntilIncomeMathTest {
    private val today = LocalDate.parse("2026-11-15")
    private val account = Account(1, "Основной", "RUB", 2_000_000L, 2_000_000L, 1_000_000L)
    private fun plan(id: Long, due: String, amount: Long, frequency: String = "ONCE") =
        Plan(id, "Платёж", amount, 1, due, frequency, false, true, LocalDate.parse(due).dayOfMonth)

    @Test fun worksOutDailyBudgetAndReserves() {
        val s = Snapshot(
            accounts = listOf(account),
            goals = listOf(Goal(1, "Копилка", 100_000L, 200_000L, true)),
            plans = listOf(plan(1, "2026-11-19", 400_000L))
        )
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-25"), today)
        assertEquals(10L, r.daysRemaining)
        assertEquals(2_000_000L, r.cashMinor)
        assertEquals(400_000L, r.unpaidPlansMinor)
        assertEquals(1_400_000L, r.spendableMinor)
        assertEquals(140_000L, r.dailyLimitMinor)
    }

    @Test fun recurringPaymentsStopBeforeIncomeDate() {
        val s = Snapshot(accounts = listOf(account), plans = listOf(plan(1, "2026-11-15", 10_000L, "WEEKLY")))
        // 15th and 22nd; a 29th payment happens on income day and is excluded.
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-29"), today)
        assertEquals(20_000L, r.unpaidPlansMinor)
    }

    @Test fun alreadyBookedPlanIsNotDeductedTwice() {
        val booked = Entry(1, "2026-11-18", Kind.EXPENSE, 1, 10_000L, 10_000L,
            "Платежи", sourceKey = "plan:1:2026-11-18")
        val s = Snapshot(accounts = listOf(account), entries = listOf(booked),
            plans = listOf(plan(1, "2026-11-18", 10_000L)))
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-25"), today)
        assertEquals(0L, r.unpaidPlansMinor)
        assertEquals(1_990_000L, r.spendableMinor)
    }

    @Test fun paymentsAlreadyCoveredByReserveAreNotDoubleCounted() {
        val s = Snapshot(accounts = listOf(account),
            plans = listOf(plan(1, "2026-11-20", 100_000L)),
            settings = Settings(extraReserveMinor = 200_000L, coveredPlansMinor = 60_000L))
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-25"), today)
        assertEquals(60_000L, r.plansCoveredByReserveMinor)
        assertEquals(1_760_000L, r.spendableMinor)
    }

    @Test fun ignoresPlansOnExcludedAccountsAndDoesNotCountTransfersAsExpenses() {
        val excluded = Account(2, "Не учитывать", "RUB", 0, 0, 1_000_000, false)
        val transfer = Entry(1, "2026-11-16", Kind.TRANSFER, 1, 30_000, 30_000,
            "Перевод", toAccount = 2, toAmountMinor = 30_000)
        val s = Snapshot(accounts = listOf(account, excluded), entries = listOf(transfer),
            plans = listOf(plan(1, "2026-11-20", 5_000),
                Plan(2, "Исключён", 50_000, 2, "2026-11-20", "ONCE", false, true, 20)))
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-25"), today)
        assertEquals(5_000L, r.unpaidPlansMinor)
        assertEquals(1_965_000L, r.spendableMinor)
    }

    @Test fun onePassCashMatchesExistingLedgerForMixedTransactions() {
        val second = Account(2, "Запасной", "RUB", 300_000L, 300_000L, 1_000_000L)
        val transactions = listOf(
            Entry(1, "2026-11-15", Kind.EXPENSE, 1, 15_000L, 15_000L, "Продукты"),
            Entry(2, "2026-11-15", Kind.INCOME, 2, 80_000L, 80_000L, "Доход"),
            Entry(3, "2026-11-15", Kind.TRANSFER, 1, 20_000L, 20_000L, "Перевод", toAccount=2, toAmountMinor=20_000L)
        )
        val s = Snapshot(accounts=listOf(account, second), entries=transactions)
        val r = UntilIncomeMath.calculate(s, LocalDate.parse("2026-11-25"), today)
        assertEquals(LedgerMath.cashBase(s), r.cashMinor)
    }

    @Test fun rejectsCurrentOrPastIncomeDay() {
        assertThrows(IllegalArgumentException::class.java) {
            UntilIncomeMath.calculate(Snapshot(), today, today)
        }
    }
}
