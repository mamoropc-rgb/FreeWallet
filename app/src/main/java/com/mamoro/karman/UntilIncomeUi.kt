package com.mamoro.karman

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val incomeDayFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU"))
private val Highlight = Color(0xFFAB79FF)

@Composable
private fun ForecastMetric(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall,
            lineHeight=13.sp,maxLines=2)
        Text(value,fontWeight=FontWeight.SemiBold,fontSize=12.sp,maxLines=1)
    }
}

/** Use the exact same flagship panel in light and dark premium themes. */
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
            Icon(Icons.Default.CalendarMonth,contentDescription=null,tint=Highlight)
            Spacer(Modifier.width(8.dp))
            Text("До дохода",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            TextButton(onClick={
                val initial=selected?.takeIf {it.isAfter(today) && !it.isAfter(today.plusYears(2))}
                    ?:today.plusDays(14)
                DatePickerDialog(context,{_,year,month,day->
                    vm.pref("nextIncomeDate",LocalDate.of(year,month+1,day).toString())
                },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
                    datePicker.minDate=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    datePicker.maxDate=today.plusYears(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }) { Text(if(selected==null)"Выбрать дату" else "Изменить дату",fontSize=12.sp) }
        }
        when {
            selected==null -> Text("Укажите дату следующего дохода — покажем доступную сумму и дневной лимит.",
                style=MaterialTheme.typography.bodyMedium)
            !selected.isAfter(today) -> Text("Дата дохода прошла. Выберите следующую дату.")
            forecast==null -> Text("Дата должна быть в пределах ближайших двух лет.")
            else -> {
                val currency=s.settings.displayCurrency
                val rate=LedgerMath.rateForDisplay(s.settings,currency)
                val amount=Money.fromBase(forecast.spendableMinor,rate)
                Text(Money.text(amount,currency),fontWeight=FontWeight.Bold,fontSize=29.sp,
                    color=if(amount<0L)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(if(amount<0L)"Дефицит до следующего дохода" else "Доступно до следующего дохода",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    ForecastMetric("Следующий доход",forecast.incomeDate.format(incomeDayFormat),Modifier.weight(1f))
                    ForecastMetric("Осталось","${forecast.daysRemaining} дн.",Modifier.weight(.78f))
                    ForecastMetric("Лимит в день",Money.text(Money.fromBase(forecast.dailyLimitMinor,rate),currency),Modifier.weight(1.2f))
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments,contentDescription=null,modifier=Modifier.size(17.dp),tint=Highlight)
                    Spacer(Modifier.width(6.dp))
                    Text("Платежи до дохода",fontSize=12.sp,modifier=Modifier.weight(1f))
                    Text("− ${Money.text(Money.fromBase(forecast.unpaidPlansMinor,rate),currency)}",
                        fontSize=12.sp,color=MaterialTheme.colorScheme.error)
                }
                if(forecast.protectedReserveMinor>0L) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings,contentDescription=null,modifier=Modifier.size(17.dp),tint=Highlight)
                        Spacer(Modifier.width(6.dp))
                        Text("Защищённый резерв",fontSize=12.sp,modifier=Modifier.weight(1f))
                        Text(Money.text(Money.fromBase(forecast.protectedReserveMinor,rate),currency),fontSize=12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    Brush.horizontalGradient(listOf(Color(0xFF713FE5),Color(0xFFBA75FF))),RoundedCornerShape(8.dp)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Сегодня, ${today.format(incomeDayFormat)}",fontSize=11.sp,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(forecast.incomeDate.format(incomeDayFormat),fontSize=11.sp,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
