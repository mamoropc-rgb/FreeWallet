package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryAnalyticsTest {
    private val a = Account(1,"Карта","RUB",100_000,100_000,1_000_000)
    private val b = Account(2,"Сбережения","RUB",0,0,1_000_000)
    private fun e(id: Long, day:String, kind:String, amount:Long, category:String,
                  account:Long=1, note:String="") =
        Entry(id,day,kind,account,amount,amount,category,note)
    private val date = LocalDate.of(2026,11,15)
    private val month = YearMonth.of(2026,11)

    @Test fun excludesTransfersInvestmentsAndCouponsFromSpend() {
        val data = Snapshot(accounts=listOf(a,b),entries=listOf(
            e(1,"2026-11-10",Kind.EXPENSE,61096,"Пятёрочка"),
            e(2,"2026-11-12",Kind.TRANSFER,20000,"Перевод"),
            e(3,"2026-11-12",Kind.BUY,40000,"Покупка облигаций"),
            e(4,"2026-11-12",Kind.SELL,10000,"Продажа облигаций"),
            e(5,"2026-11-12",Kind.COUPON,500,"Купон"),
            e(6,"2026-11-14",Kind.INCOME,70000,"Зарплата")
        ))
        val summary = HistoryAnalytics.calculate(data,month,today=date)
        assertEquals(61096L, summary.spentMinor)
        assertEquals(70000L, summary.incomeMinor)
        assertEquals(8904L, summary.netIncomeMinor)
        assertNull(summary.averageSpentMinor)
    }

    @Test fun usesChosenAccountOnlyAndHistoricalBaseValue() {
        val data=Snapshot(accounts=listOf(a,b),entries=listOf(
            e(1,"2026-11-05",Kind.EXPENSE,3000,"Магазин"),
            e(2,"2026-11-06",Kind.EXPENSE,5000,"Аптека",account=2)
        ))
        val summary=HistoryAnalytics.calculate(data,month,setOf(2L),date)
        assertEquals(5000L,summary.spentMinor)
        assertEquals("Аптека",summary.topCategories.single().name)
        assertEquals(-5000L,summary.balanceMinor)
    }

    @Test fun comparisonUsesPriorActiveMonthToSameDayOnly() {
        val data=Snapshot(accounts=listOf(a),entries=listOf(
            e(1,"2026-10-04",Kind.EXPENSE,3000,"Кафе"),
            e(2,"2026-10-20",Kind.EXPENSE,9000,"Кафе"),
            e(3,"2026-11-06",Kind.EXPENSE,2000,"Кафе")
        ))
        val summary=HistoryAnalytics.calculate(data,month,today=date)
        assertEquals(1,summary.comparisonMonths)
        assertEquals(3000L,summary.averageSpentMinor)
        assertEquals(-1000L,summary.differenceFromAverageMinor)
        assertEquals(-1000L,summary.categoryChanges.single().differenceMinor)
    }

    @Test fun multiplePurchasesRemainSeparateAndCountedWithoutAdditionalAmounts() {
        val data=Snapshot(accounts=listOf(a),entries=listOf(
            e(1,"2026-11-15",Kind.EXPENSE,61096,"Пятёрочка"),
            e(2,"2026-11-15",Kind.EXPENSE,50000,"Фастфуд")
        ))
        val summary=HistoryAnalytics.calculate(data,month,today=date)
        assertEquals(111096L,summary.spentMinor)
        assertEquals(2,summary.topMerchants.size)
        assertEquals(0L,summary.incomeMinor)
        assertTrue(summary.topCategories.all { it.name != "Кешбэк" })
    }

    @Test fun previousMonthAndNoHistoryAreSafe() {
        val summary=HistoryAnalytics.calculate(Snapshot(),YearMonth.of(2026,10),today=date)
        assertEquals(31,summary.throughDay)
        assertTrue(summary.topCategories.isEmpty())
        assertNull(summary.averageSpentMinor)
    }
}
