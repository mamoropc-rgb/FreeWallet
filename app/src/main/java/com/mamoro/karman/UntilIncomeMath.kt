package com.mamoro.karman

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** All amounts are in the base currency's minor units (kopecks for RUB). */
data class UntilIncomeForecast(
    val incomeDate: LocalDate,
    val daysRemaining: Long,
    val cashMinor: Long,
    val protectedReserveMinor: Long,
    val unpaidPlansMinor: Long,
    val plansCoveredByReserveMinor: Long,
    val spendableMinor: Long,
    val dailyLimitMinor: Long
)

/** Forecasts spendable cash before the NEXT expected income, without booking future income. */
object UntilIncomeMath {
    fun calculate(s: Snapshot, incomeDate: LocalDate, today: LocalDate = LocalDate.now()): UntilIncomeForecast {
        require(incomeDate.isAfter(today)) { "Дата дохода должна быть позже сегодняшнего дня" }
        require(!incomeDate.isAfter(today.plusYears(2))) { "Выберите дату в пределах двух лет" }

        val goals = s.goals.asSequence().filter { it.reserve }
            .fold(0L) { total, goal -> Math.addExact(total, goal.savedMinor) }
        val rawReserve = Math.addExact(goals, s.settings.extraReserveMinor)
        val shared = s.settings.sharedReserveMinor.coerceIn(0L, rawReserve.coerceAtLeast(0L))
        val reserve = (rawReserve - shared).coerceAtLeast(0L)
        val activeAccounts = s.accounts.asSequence().filter { it.included }.associateBy { it.id }
        // A recorded plan transaction is already reflected in cash. Never subtract it twice.
        val postedKeys = s.entries.mapNotNull { it.sourceKey }.toHashSet()
        var upcoming = 0L
        for (plan in s.plans) {
            if (!plan.reserve) continue
            val account = activeAccounts[plan.accountId] ?: continue
            var date = LocalDate.parse(plan.due)
            var count = 0
            while (date.isBefore(incomeDate)) {
                check(count++ < 5000) { "Слишком много повторений запланированного платежа" }
                if ("plan:${plan.id}:$date" !in postedKeys) {
                    upcoming = Math.addExact(upcoming, Money.toBase(plan.amountMinor, account.rateMicros))
                }
                if (plan.frequency == "ONCE") break
                val next = LedgerMath.nextDate(date, plan.frequency, plan.anchorDay)
                check(next.isAfter(date)) { "Неверная периодичность платежа" }
                date = next
            }
        }
        // Covered payments are already included in the protected reserve and cannot be deducted again.
        val covered = s.settings.coveredPlansMinor.coerceIn(0L, minOf(upcoming, reserve))
        // Single pass over operations: unlike per-account scans, work is O(accounts + entries).
        var cash = activeAccounts.values.fold(0L) { total, account -> Math.addExact(total, account.openingBaseMinor) }
        for (entry in s.entries) {
            if (entry.fromAccount in activeAccounts) {
                val change = when (entry.kind) {
                    Kind.INCOME, Kind.COUPON, Kind.SELL -> entry.baseMinor
                    else -> -entry.baseMinor
                }
                cash = Math.addExact(cash, change)
            }
            if (entry.kind == Kind.TRANSFER && entry.toAccount in activeAccounts) {
                cash = Math.addExact(cash, entry.baseMinor)
            }
        }
        val spendable = Math.subtractExact(Math.subtractExact(cash, reserve), upcoming - covered)
        val days = ChronoUnit.DAYS.between(today, incomeDate)
        return UntilIncomeForecast(
            incomeDate = incomeDate, daysRemaining = days,
            cashMinor = cash, protectedReserveMinor = reserve,
            unpaidPlansMinor = upcoming, plansCoveredByReserveMinor = covered,
            spendableMinor = spendable, dailyLimitMinor = spendable.coerceAtLeast(0L) / days
        )
    }
}
