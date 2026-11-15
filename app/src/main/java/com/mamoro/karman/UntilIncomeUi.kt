package com.mamoro.karman

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val incomeDayFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU"))
private val Highlight = Color(0xFFBE88FF)

private data class IncomeMetric(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val negative: Boolean = false
)

@Composable
private fun IncomeMetricCell(item: IncomeMetric, modifier:Modifier = Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Icon(item.icon, contentDescription=null,
            tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
        Text(item.label, fontSize=10.sp,lineHeight=12.sp,maxLines=2,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.value,fontWeight=FontWeight.SemiBold,fontSize=11.sp,lineHeight=13.sp,
            maxLines=2,overflow=TextOverflow.Ellipsis,
            color=if(item.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

/** Keeps the four values in a glass inset; stacks into 2x2 at narrower widths. */
@Composable
private fun IncomeMetrics(items:List<IncomeMetric>) {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF080C1C)
    val container = RoundedCornerShape(17.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(container)
        .background(if(dark) Color(0xFF12112F).copy(alpha=.37f) else Color.White.copy(alpha=.35f))
        .border(0.6.dp, Color.White.copy(alpha=if(dark) .10f else .43f),container)
        .padding(horizontal=10.dp,vertical=11.dp)) {
        if (maxWidth < 318.dp) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        pair.forEach { item -> IncomeMetricCell(item,Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                items.forEachIndexed { i, metric ->
                    IncomeMetricCell(metric, Modifier.weight(1f))
                    if(i != items.lastIndex) Box(Modifier.width(1.dp).height(64.dp).background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.22f)))
                }
            }
        }
    }
}

@Composable
private fun IncomeTimeline(today:LocalDate, nextIncome:LocalDate) {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF080C1C)
    // The track shows the date interval. Without the prior payday it is intentionally
    // NOT presented as a percentage of time elapsed.
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF8444FB), Color(0xFFC27BFF),
                if(dark) Color(0xFF7247AC) else Color(0xFFD1ACFF)))))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(
            if(dark) Color(0xFFEED8FF) else Color(0xFF974DF5)))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Сегодня, ${today.format(incomeDayFormat)}",fontSize=10.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(nextIncome.format(incomeDayFormat),fontSize=10.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The light shimmer and the decorative glass image are drawn behind this content. */
@Composable
fun UntilIncomeCard(s:Snapshot,vm:KarmanViewModel,today:LocalDate) {
    val context=LocalContext.current
    val selected=remember(s.settings.nextIncomeDate) {
        s.settings.nextIncomeDate?.let {runCatching { LocalDate.parse(it) }.getOrNull()}
    }
    val forecast=remember(s,selected,today) {
        selected?.takeIf {it.isAfter(today) && !it.isAfter(today.plusYears(2))}
            ?.let {UntilIncomeMath.calculate(s,it,today)}
    }
    PremiumGlassPanel(Modifier.fillMaxWidth(),prominent=true) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth,contentDescription=null,tint=Highlight,
                modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("До дохода",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),
                style=MaterialTheme.typography.titleMedium)
            Surface(onClick={
                val initial=selected?.takeIf {it.isAfter(today) && !it.isAfter(today.plusYears(2))}
                    ?:today.plusDays(14)
                DatePickerDialog(context,{_,year,month,day->
                    vm.pref("nextIncomeDate",LocalDate.of(year,month+1,day).toString())
                },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
                    datePicker.minDate=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    datePicker.maxDate=today.plusYears(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            },shape=RoundedCornerShape(22.dp),
                color=MaterialTheme.colorScheme.primary.copy(alpha=.20f)) {
                Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.CalendarMonth,null,modifier=Modifier.size(15.dp),
                        tint=MaterialTheme.colorScheme.primary)
                    Text(if(selected==null)"Выбрать дату" else "Изменить дату",
                        color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp,maxLines=1)
                }
            }
        }
        when {
            selected==null -> {
                Spacer(Modifier.height(5.dp))
                Text("Укажите дату следующего дохода",fontWeight=FontWeight.SemiBold,
                    style=MaterialTheme.typography.titleMedium)
                Text("Посчитаем доступную сумму, платежи и лимит на день.",
                    style=MaterialTheme.typography.bodySmall)
            }
            !selected.isAfter(today) -> Text("Дата дохода прошла. Выберите следующую дату.")
            forecast==null -> Text("Дата должна быть в пределах ближайших двух лет.")
            else -> {
                val currency=s.settings.displayCurrency
                val rate=LedgerMath.rateForDisplay(s.settings,currency)
                val amount=Money.fromBase(forecast.spendableMinor,rate)
                Spacer(Modifier.height(4.dp))
                Text(Money.text(amount,currency),fontWeight=FontWeight.Bold,fontSize=32.sp,
                    color=if(amount<0L)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(if(amount<0L)"Дефицит до следующего дохода" else "Доступно до следующего дохода",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                IncomeMetrics(listOf(
                    IncomeMetric(Icons.Default.CalendarMonth,"Следующий доход",forecast.incomeDate.format(incomeDayFormat)),
                    IncomeMetric(Icons.Default.HourglassBottom,"Осталось","${forecast.daysRemaining} дней"),
                    IncomeMetric(Icons.Default.Storage,"Лимит в день",
                        Money.text(Money.fromBase(forecast.dailyLimitMinor,rate),currency)),
                    IncomeMetric(Icons.Default.CreditCard,"Будущие платежи",
                        "− ${Money.text(Money.fromBase(forecast.unpaidPlansMinor,rate),currency)}",
                        forecast.unpaidPlansMinor > 0L)
                ))
                if(forecast.protectedReserveMinor>0L) {
                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=5.dp)) {
                        Icon(Icons.Default.Savings,null,modifier=Modifier.size(16.dp),tint=Highlight)
                        Spacer(Modifier.width(6.dp))
                        Text("Защищённый резерв",fontSize=11.sp,modifier=Modifier.weight(1f))
                        Text(Money.text(Money.fromBase(forecast.protectedReserveMinor,rate),currency),fontSize=11.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                IncomeTimeline(today,forecast.incomeDate)
            }
        }
    }
}
