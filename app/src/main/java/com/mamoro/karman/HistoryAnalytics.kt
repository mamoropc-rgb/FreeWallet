package com.mamoro.karman

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** Snapshot-only analytics: no database writes, no notification content and no floating-point money. */
data class AnalyticsDay(
    val day: Int,
    val expenseCumulativeMinor: Long,
    val incomeCumulativeMinor: Long,
    val averageExpenseCumulativeMinor: Long?
)

data class AnalyticsLeader(val name: String, val amountMinor: Long, val count: Int)
data class AnalyticsCategoryChange(val name: String, val currentMinor: Long, val averageMinor: Long) {
    val differenceMinor: Long get() = currentMinor - averageMinor
}

data class HistoryAnalyticsSummary(
    val month: YearMonth,
    val throughDay: Int,
    val spentMinor: Long,
    val incomeMinor: Long,
    val balanceMinor: Long,
    val averageSpentMinor: Long?,
    val comparisonMonths: Int,
    val timeline: List<AnalyticsDay>,
    val topCategories: List<AnalyticsLeader>,
    val topMerchants: List<AnalyticsLeader>,
    val categoryChanges: List<AnalyticsCategoryChange>
) {
    val netIncomeMinor: Long get() = incomeMinor - spentMinor
    val differenceFromAverageMinor: Long? get() = averageSpentMinor?.let { spentMinor - it }
}

object HistoryAnalytics {
    /** A transfer between one's own accounts or a portfolio trade is not consumer spending. */
    private fun isExpense(e: Entry) = e.kind == Kind.EXPENSE
    private fun isIncome(e: Entry) = e.kind == Kind.INCOME

    /** Merchant is often stored as the transaction category for notification-derived purchases. */
    private fun merchant(e: Entry): String =
        e.note.trim().takeIf { it.isNotEmpty() && e.sourceKey?.startsWith("bank:") != true }
            ?: e.category.trim().ifEmpty { "Без названия" }

    fun calculate(
        snapshot: Snapshot,
        month: YearMonth,
        accountIds: Set<Long>? = null,
        today: LocalDate = LocalDate.now()
    ): HistoryAnalyticsSummary {
        val throughDay = when {
            month > YearMonth.from(today) -> 0
            month == YearMonth.from(today) -> today.dayOfMonth
            else -> month.lengthOfMonth()
        }
        val lookback = (1L..3L).map { month.minusMonths(it) }
        val monthKeys = (listOf(month) + lookback).associateBy { it.toString() }
        val relevant = snapshot.entries.asSequence().filter { e ->
            (accountIds == null || e.fromAccount in accountIds) && (isExpense(e) || isIncome(e))
        }.mapNotNull { e ->
            // Strict dates: corrupt historical rows must not crash the History screen.
            val date = runCatching { LocalDate.parse(e.day) }.getOrNull() ?: return@mapNotNull null
            if (monthKeys[date.toString().take(7)] == null) return@mapNotNull null
            e to date
        }.toList()
        val activeMonths = lookback.filter { prior ->
            relevant.any { (_, date) -> YearMonth.from(date) == prior }
        }
        // Pre-aggregate by day once, then build cumulative series in O(rows + days * months).
        val dailyExpense = (listOf(month) + lookback).associateWith { LongArray(it.lengthOfMonth() + 1) }
        val dailyIncome = (listOf(month) + lookback).associateWith { LongArray(it.lengthOfMonth() + 1) }
        relevant.forEach { (entry,date) ->
            val key=YearMonth.from(date)
            if(isExpense(entry)) dailyExpense.getValue(key)[date.dayOfMonth] += entry.baseMinor
            if(isIncome(entry)) dailyIncome.getValue(key)[date.dayOfMonth] += entry.baseMinor
        }
        (listOf(month) + lookback).forEach { key ->
            for (day in 1..key.lengthOfMonth()) {
                dailyExpense.getValue(key)[day] += dailyExpense.getValue(key)[day-1]
                dailyIncome.getValue(key)[day] += dailyIncome.getValue(key)[day-1]
            }
        }
        val days = (1..month.lengthOfMonth()).map { day ->
            val target = min(day, throughDay)
            val spent = dailyExpense.getValue(month)[target]
            val income = dailyIncome.getValue(month)[target]
            val avg = if (activeMonths.isEmpty()) null else activeMonths.sumOf { prior ->
                dailyExpense.getValue(prior)[min(day,prior.lengthOfMonth())]
            } / activeMonths.size
            AnalyticsDay(day, spent, income, avg)
        }
        val selected = relevant.filter { (_, date) ->
            YearMonth.from(date) == month && date.dayOfMonth <= throughDay
        }
        val expenses = selected.filter { (e, _) -> isExpense(e) }.map { it.first }
        val spent = expenses.sumOf { it.baseMinor }
        val income = selected.asSequence().filter { (e, _) -> isIncome(e) }.sumOf { it.first.baseMinor }
        val categoryLeaders = expenses.groupBy { it.category.trim().ifEmpty { "Без категории" } }
            .map { (name, entries) -> AnalyticsLeader(name, entries.sumOf { it.baseMinor }, entries.size) }
            .sortedWith(compareByDescending<AnalyticsLeader> { it.amountMinor }.thenBy { it.name })
        val merchantLeaders = expenses.groupBy(::merchant)
            .map { (name, entries) -> AnalyticsLeader(name, entries.sumOf { it.baseMinor }, entries.size) }
            .sortedWith(compareByDescending<AnalyticsLeader> { it.count }.thenByDescending { it.amountMinor })
        val previousCategoryTotals = activeMonths.map { prior ->
            relevant.asSequence().filter { (e, date) ->
                isExpense(e) && YearMonth.from(date) == prior &&
                    date.dayOfMonth <= min(throughDay, prior.lengthOfMonth())
            }.groupBy { (e, _) -> e.category.trim().ifEmpty { "Без категории" } }
                .mapValues { (_, rows) -> rows.sumOf { it.first.baseMinor } }
        }
        val currentTotals = categoryLeaders.associate { it.name to it.amountMinor }
        val compared = if (activeMonths.isEmpty()) emptyList() else
            (currentTotals.keys + previousCategoryTotals.flatMap { it.keys }).distinct().map { name ->
                val avg = previousCategoryTotals.sumOf { it[name] ?: 0L } / activeMonths.size
                AnalyticsCategoryChange(name, currentTotals[name] ?: 0L, avg)
            }.filter { it.differenceMinor != 0L }
                .sortedWith(compareByDescending<AnalyticsCategoryChange> {
                    // Prevent overflow for Long.MIN_VALUE (although monetary input is bounded).
                    if (it.differenceMinor < 0L) -it.differenceMinor else it.differenceMinor
                }.thenBy { it.name })
        val included = snapshot.accounts.filter { accountIds?.contains(it.id) ?: it.included }
        val cash = included.sumOf { LedgerMath.accountBase(it, snapshot.entries) }
        return HistoryAnalyticsSummary(month, throughDay, spent, income, cash,
            days.getOrNull((throughDay - 1).coerceAtLeast(0))?.averageExpenseCumulativeMinor,
            activeMonths.size, days, categoryLeaders, merchantLeaders, compared)
    }
}
