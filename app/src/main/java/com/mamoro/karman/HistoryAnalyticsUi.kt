package com.mamoro.karman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private val ChartViolet = Color(0xFFAA73FF)
private val ChartGreen = Color(0xFF56E0AC)
private val ChartOrange = Color(0xFFFFAA43)

@Composable
private fun AnalyticsCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    PremiumGlassPanel(modifier) { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp),content=content) }
}

/** Cached snapshot calculation; expensive grouping does not rerun during light effects. */
@Composable
fun HistoryAnalyticsDashboard(s:Snapshot,month:YearMonth,accountIds:Set<Long>?,today:LocalDate) {
    val result=remember(s,month,accountIds,today) { HistoryAnalytics.calculate(s,month,accountIds,today) }
    val primary=MaterialTheme.colorScheme.onSurface
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant
    val monthLabel=month.month.getDisplayName(TextStyle.FULL_STANDALONE,Locale.forLanguageTag("ru-RU"))
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            AnalyticsCard(Modifier.weight(1f)) {
                Icon(Icons.Default.AccountBalanceWallet,null,tint=ChartViolet,modifier=Modifier.size(21.dp))
                Text(CompactAnalyticsMoney(result.balanceMinor),color=primary,fontWeight=FontWeight.Bold,
                    maxLines=2,fontSize=13.sp,lineHeight=15.sp)
                Text("Денег всего",color=secondary,style=MaterialTheme.typography.labelSmall,maxLines=2)
            }
            AnalyticsCard(Modifier.weight(1f)) {
                Icon(Icons.Default.BarChart,null,tint=ChartOrange,modifier=Modifier.size(21.dp))
                Text(CompactAnalyticsMoney(result.netIncomeMinor),color=primary,fontWeight=FontWeight.Bold,
                    maxLines=2,fontSize=13.sp,lineHeight=15.sp)
                Text("Остаток от дохода",color=secondary,style=MaterialTheme.typography.labelSmall,maxLines=2)
            }
            AnalyticsCard(Modifier.weight(1f)) {
                Icon(Icons.Default.ShowChart,null,tint=ChartGreen,modifier=Modifier.size(21.dp))
                val diff=result.differenceFromAverageMinor
                Text(when {
                    diff==null->"Нет сравнения"
                    diff<0L->"Ниже среднего"
                    diff>0L->"Выше среднего"
                    else->"На уровне среднего"
                },color=primary,fontWeight=FontWeight.SemiBold,maxLines=2,fontSize=12.sp,lineHeight=14.sp)
                Text(if(diff==null) "Нет истории" else "На ${CompactAnalyticsMoney(kotlin.math.abs(diff))}",
                    color=secondary,style=MaterialTheme.typography.labelSmall,maxLines=2)
            }
        }
        Text("Баланс — на текущий момент; доходы и расходы — за выбранный месяц.",
            color=secondary,style=MaterialTheme.typography.labelSmall)
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Как проходит $monthLabel",fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleLarge,color=primary)
            Text("Только реальные доходы и расходы, без переводов между своими счетами",
                color=secondary,style=MaterialTheme.typography.bodySmall)
        }
        AnalyticsCard {
            Text("Расходы к ${result.throughDay} числу",fontWeight=FontWeight.SemiBold,color=primary)
            if(result.throughDay==0) Text("Выбранный месяц ещё не наступил",color=secondary)
            else {
                val avg=result.averageSpentMinor
                AmountComparisonBar("Текущие",result.spentMinor,max(result.spentMinor,avg?:0L).coerceAtLeast(1L),ChartOrange)
                if(avg!=null) {
                    AmountComparisonBar("Средние за ${result.comparisonMonths} мес.",avg,
                        max(result.spentMinor,avg).coerceAtLeast(1L),ChartViolet)
                } else Text("Сравнение появится после операций за предыдущие месяцы",
                    color=secondary,style=MaterialTheme.typography.bodySmall)
            }
        }
        AnalyticsCard {
            Text("Траты по дням",fontWeight=FontWeight.SemiBold,color=primary)
            val diff=result.differenceFromAverageMinor
            if(diff==null) Text("Фиолетовая линия — накопленные расходы",
                color=secondary,style=MaterialTheme.typography.labelSmall)
            else Text("Фиолетовая — расходы · пунктир — средние за предыдущие месяцы",
                color=secondary,style=MaterialTheme.typography.labelSmall)
            SpendingCurve(result.timeline,result.throughDay,Modifier.fillMaxWidth().height(174.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("1",color=secondary,style=MaterialTheme.typography.labelSmall)
                Text("${result.throughDay.coerceAtLeast(0)}",color=secondary,style=MaterialTheme.typography.labelSmall)
                Text("${month.lengthOfMonth()}",color=secondary,style=MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AnalyticsCard(Modifier.weight(1f)) {
                Text(Money.text(result.spentMinor,"RUB"),fontWeight=FontWeight.SemiBold,color=primary)
                Text("Расходы",color=secondary,style=MaterialTheme.typography.bodySmall)
            }
            AnalyticsCard(Modifier.weight(1f)) {
                Text(Money.text(result.incomeMinor,"RUB"),fontWeight=FontWeight.SemiBold,color=ChartGreen)
                Text("Доходы",color=secondary,style=MaterialTheme.typography.bodySmall)
            }
        }
        AnalyticsCard {
            Text("Больше всего тратите здесь",fontWeight=FontWeight.SemiBold,color=primary,
                style=MaterialTheme.typography.titleMedium)
            if(result.topCategories.isEmpty()) Text("Пока нет расходов за выбранный период",color=secondary)
            else VerticalRankBars(result.topCategories.take(4),color=ChartViolet,byFrequency=false)
        }
        AnalyticsCard {
            Text("Чаще всего повторяются",fontWeight=FontWeight.SemiBold,color=primary,
                style=MaterialTheme.typography.titleMedium)
            if(result.topMerchants.isEmpty()) Text("Нет покупок для рейтинга",color=secondary)
            else VerticalRankBars(result.topMerchants.sortedByDescending { it.count }.take(4),
                color=ChartGreen,byFrequency=true)
        }
        if(result.comparisonMonths>0) {
            val below=result.categoryChanges.filter { it.differenceMinor < 0L }
                .sortedBy { it.differenceMinor }.take(5)
            val above=result.categoryChanges.filter { it.differenceMinor > 0L }
                .sortedByDescending { it.differenceMinor }.take(5)
            if(below.isNotEmpty()) AnalyticsCard {
                Text("Здесь траты ниже среднего",fontWeight=FontWeight.SemiBold,color=primary,
                    style=MaterialTheme.typography.titleMedium)
                below.forEach { CategoryComparisonRow(it,lower=true) }
            }
            if(above.isNotEmpty()) AnalyticsCard {
                Text("Здесь траты выше среднего",fontWeight=FontWeight.SemiBold,color=primary,
                    style=MaterialTheme.typography.titleMedium)
                above.forEach { CategoryComparisonRow(it,lower=false) }
            }
        }
        Text("Средние значения рассчитываются только по месяцам с операциями (не более трёх предыдущих). Изменение курсов валют не переписывает исторические суммы.",
            color=secondary,style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(75.dp))
    }
}

@Composable
private fun AmountComparisonBar(label:String,value:Long,maxValue:Long,color:Color) {
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=secondary,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        Text(Money.text(value,"RUB"),fontWeight=FontWeight.Medium)
    }
    Box(Modifier.fillMaxWidth().height(12.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((value.toFloat()/maxValue.toFloat()).coerceIn(0f,1f))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha=.68f),color)),RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun VerticalRankBars(data:List<AnalyticsLeader>,color:Color,byFrequency:Boolean) {
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue=if(byFrequency) data.maxOfOrNull { it.count.toLong() }?.coerceAtLeast(1L) ?: 1L
        else data.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(178.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Bottom) {
        data.forEachIndexed { index,item ->
            val value=if(byFrequency)item.count.toLong() else item.amountMinor
            val ratio=(value.toFloat()/maxValue.toFloat()).coerceIn(0.06f,1f)
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height((24f+ratio*89f).dp)
                    .background(Brush.verticalGradient(listOf(color.copy(alpha=(.74f-index*.08f).coerceAtLeast(.28f)),
                        color.copy(alpha=.18f))),RoundedCornerShape(topStart=12.dp,topEnd=12.dp)),
                    contentAlignment=Alignment.BottomCenter) {
                    Text(if(byFrequency) "${item.count} раз" else ShortMoney(item.amountMinor),
                        color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp,maxLines=1,
                        modifier=Modifier.padding(bottom=10.dp),fontWeight=FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.name,color=secondary,style=MaterialTheme.typography.labelSmall,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private fun CompactAnalyticsMoney(minor:Long):String = if(minor%100L==0L) {
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ru-RU"))
        .format(minor/100L) + " ₽"
} else Money.text(minor,"RUB")

private fun ShortMoney(minor:Long):String=if(minor>=100_000L) "${minor/100_000} тыс. ₽"
    else Money.text(minor,"RUB")

@Composable
private fun CategoryComparisonRow(row:AnalyticsCategoryChange,lower:Boolean) {
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Default.PieChart,null,modifier=Modifier.size(25.dp),tint=if(lower)ChartGreen else ChartOrange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("В среднем ${Money.text(row.averageMinor,"RUB")}",color=secondary,
                style=MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment=Alignment.End) {
            Text(Money.text(row.currentMinor,"RUB"),fontWeight=FontWeight.SemiBold,
                style=MaterialTheme.typography.bodySmall)
            Text("${if(lower)"↓" else "↑"} ${Money.text(kotlin.math.abs(row.differenceMinor),"RUB")}",
                color=if(lower)ChartGreen else ChartOrange,style=MaterialTheme.typography.labelSmall)
        }
    }
}

/** Small, draw-only plot: one pass, no animation re-layout or third-party chart dependency. */
@Composable
private fun SpendingCurve(points:List<AnalyticsDay>,throughDay:Int,modifier:Modifier=Modifier) {
    val isDark=MaterialTheme.colorScheme.background==Color(0xFF080C1C)
    Canvas(modifier) {
        if(points.isEmpty()) return@Canvas
        val maxAmount=points.maxOf { max(it.expenseCumulativeMinor,it.averageExpenseCumulativeMinor?:0L) }
            .coerceAtLeast(1L)
        val minY=8f
        val usableH=size.height-22f
        fun pos(i:Int,value:Long):Offset = Offset(
            4f+(i.toFloat()/(points.size-1).coerceAtLeast(1))*(size.width-8f),
            minY+usableH*(1f-value.toFloat()/maxAmount.toFloat()))
        val actual=points.take(throughDay.coerceIn(0,points.size))
        if(actual.isNotEmpty()) {
            val path=Path().apply {
                var previous:Offset?=null
                actual.forEachIndexed { i,p ->
                    val o=pos(i,p.expenseCumulativeMinor)
                    val before=previous
                    if(before==null) moveTo(o.x,o.y) else {
                        val mid=(before.x+o.x)/2f
                        cubicTo(mid,before.y,mid,o.y,o.x,o.y)
                    }
                    previous=o
                }
            }
            val first=pos(0,actual.first().expenseCumulativeMinor)
            val last=pos(actual.lastIndex,actual.last().expenseCumulativeMinor)
            val fill=Path().apply {
                addPath(path)
                lineTo(last.x,size.height)
                lineTo(first.x,size.height)
                close()
            }
            drawPath(fill,Brush.verticalGradient(listOf(ChartViolet.copy(alpha=.22f),Color.Transparent)))
            drawPath(path,ChartViolet,style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round))
            drawCircle(ChartViolet,5.dp.toPx(),last)
            drawCircle(if(isDark)Color(0xFF15172D) else Color.White,2.4.dp.toPx(),last)
        }
        if(points.any { it.averageExpenseCumulativeMinor!=null }) {
            val path=Path().apply { points.forEachIndexed { i,p ->
                val v=p.averageExpenseCumulativeMinor ?: return@forEachIndexed
                val o=pos(i,v);if(i==0)moveTo(o.x,o.y) else lineTo(o.x,o.y)
            } }
            drawPath(path,Color.Gray.copy(alpha=.65f),style=Stroke(width=2.dp.toPx(),
                pathEffect=PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(),7.dp.toPx()))))
        }
    }
}
