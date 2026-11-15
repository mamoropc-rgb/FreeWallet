package com.mamoro.karman

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

private val Violet = Color(0xFF8C4DFF)
private val SoftViolet = Color(0xFFA46BFF)
private val Mint = Color(0xFF48DBAB)

/** Decoration and animations are selected in Settings: MAX, BALANCED or FAST. */
@Composable
fun PremiumGlassPanel(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.colorScheme
    val dark = c.background == Color(0xFF080C1C)
    val quality = LocalPremiumGraphics.current
    val corners = RoundedCornerShape(22.dp)
    val start = if (dark) (if (prominent) Color(0xFF33205A) else Color(0xFF202844))
                else (if (prominent) Color(0xFFE5D8FF) else Color(0xFFF5F5FF))
    val end = if (dark) (if (prominent) Color(0xFF1A1536) else Color(0xFF151A30))
              else (if (prominent) Color(0xFFF2EAFF) else Color(0xFFE9E8FE))
    val surface = if (quality == "FAST") Modifier.background(c.surface, corners)
                  else Modifier.background(Brush.linearGradient(listOf(start, end)), corners)
    Column(
        modifier = modifier
            .then(if (quality == "MAX") Modifier.shadow(12.dp, corners, ambientColor = Violet.copy(alpha=.25f)) else Modifier)
            .then(surface)
            .clip(corners)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun PremiumHeader(s: Snapshot, vm: KarmanViewModel) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF080C1C)
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if(isDark) Color(0xFF392260) else Color(0xFFE8D9FF)),
            contentAlignment=Alignment.Center) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = Violet, modifier=Modifier.size(26.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Кошелёк", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleLarge)
            Text("Финансы под контролем", style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surface),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={vm.pref("themeMode", "LIGHT")}, modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.LightMode,"Светлая тема",modifier=Modifier.size(20.dp),
                    tint=if(!isDark) Violet else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={vm.pref("themeMode", "DARK")},modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.DarkMode,"Тёмная тема",modifier=Modifier.size(20.dp),
                    tint=if(isDark) SoftViolet else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BalanceCardsDecoration() {
    if (LocalPremiumGraphics.current == "FAST") return
    Box(Modifier.size(width=86.dp,height=85.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.size(74.dp,50.dp).graphicsLayer { rotationZ=-25f; translationY=-13f }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFBD8FFF),Color(0xFF5239B1)))))
        Box(Modifier.size(80.dp,53.dp).graphicsLayer { rotationZ=-14f; translationY=13f }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF9A74FF),Color(0xFF4C2D9B)))),
            contentAlignment=Alignment.CenterEnd) {
            Icon(Icons.Default.Wifi,contentDescription=null,tint=Color.White.copy(alpha=.65f),
                modifier=Modifier.padding(end=10.dp))
        }
    }
}

@Composable
private fun PremiumBalance(s:Snapshot,vm:KarmanViewModel) {
    val base=remember(s) { LedgerMath.totalBase(s) }
    PremiumGlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Общий баланс",color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
                Text(Money.text(LedgerMath.shownBalance(s),s.settings.displayCurrency),
                    fontSize=29.sp,fontWeight=FontWeight.Bold,maxLines=1,
                    overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(listOf("RUB" to "₽","USD" to "$","EUR" to "€","BYN" to "Br")) { (code, sign) ->
                        val selected=s.settings.displayCurrency==code
                        val amount = Money.fromBase(base,
                            LedgerMath.rateForDisplay(s.settings, code))
                        Surface(onClick={vm.pref("displayCurrency",code)},shape=RoundedCornerShape(12.dp),
                            color=if(selected)Violet.copy(alpha=.33f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f)) {
                            Text("$sign ${Money.text(amount, code).substringBeforeLast(' ')}",
                                modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),
                                fontSize=11.sp,maxLines=1)
                        }
                    }
                }
            }
            BalanceCardsDecoration()
        }
        Text("Валюта отображения не меняет исходный баланс",fontSize=10.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickAction(title:String, icon: @Composable () -> Unit,click:()->Unit, modifier:Modifier=Modifier) {
    Surface(onClick=click,modifier=modifier.height(68.dp),shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.82f)) {
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,
            horizontalAlignment=Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(3.dp))
            Text(title,style=MaterialTheme.typography.labelSmall,maxLines=1)
        }
    }
}

@Composable
fun PremiumHomeTab(
    s: Snapshot, vm: KarmanViewModel, today:java.time.LocalDate,
    onQuick: (Template) -> Unit, onNavigate: (Int) -> Unit,
    onNewOperation:(String)->Unit
) {
    val month = remember(today) { YearMonth.from(today) }
    val income=remember(s,month) { LedgerMath.monthIncome(s,month) }
    val expenses=remember(s,month) { LedgerMath.monthSpent(s,month) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=10.dp,bottom=110.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item { PremiumHeader(s,vm) }
        item { PremiumBalance(s,vm) }
        item { UntilIncomeCard(s,vm,today) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                QuickAction("Доход",{Icon(Icons.Default.South, null, tint=Mint)},
                    {onNewOperation(Kind.INCOME)},Modifier.weight(1f))
                QuickAction("Расход",{Icon(Icons.Default.North, null, tint=Color(0xFFFF818A))},
                    {onNewOperation(Kind.EXPENSE)},Modifier.weight(1f))
                QuickAction("Перевод",{Icon(Icons.Default.SwapHoriz,null,tint=SoftViolet)},
                    {onNewOperation(Kind.TRANSFER)},Modifier.weight(1f))
                QuickAction("Счета",{Icon(Icons.Default.CreditCard,null,tint=SoftViolet)},
                    {onNavigate(3)},Modifier.weight(1f))
            }
        }
        item {
            PremiumGlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Цели",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={onNavigate(2)}) { Text("Все цели") }
                }
                val goal=s.goals.firstOrNull()
                if(goal!=null) {
                    val fraction=(goal.savedMinor.toDouble()/goal.targetMinor.coerceAtLeast(1L)).toFloat().coerceIn(0f,1f)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings,null,tint=SoftViolet)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(goal.title,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${Money.text(goal.savedMinor,"RUB")} / ${Money.text(goal.targetMinor,"RUB")}",
                                fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(fraction*100).toInt()}%",fontWeight=FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(10.dp)),color=Violet,
                        trackColor=MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Text("Добавьте первую цель во вкладке «План»",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(s.templates.isNotEmpty()) {
            item { Text("Быстрые шаблоны",fontWeight=FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(s.templates,key={it.id}) {t ->
                        AssistChip(onClick={onQuick(t)},label={Text(t.title)},
                            leadingIcon={Icon(Icons.Default.Bolt,null,modifier=Modifier.size(17.dp))})
                    }
                }
            }
        }
        item {
            PremiumGlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Последние операции",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={onNavigate(1)}){Text("Все операции")}
                }
                if(s.entries.isEmpty()) Text("Нажмите +, чтобы добавить первую операцию",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                s.entries.take(4).forEach { e -> TransactionRow(e,s) }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PremiumGlassPanel(Modifier.weight(1f)) {
                    Text("Доходы · месяц",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.text(income,"RUB"),fontWeight=FontWeight.SemiBold,color=Mint)
                }
                PremiumGlassPanel(Modifier.weight(1f)) {
                    Text("Расходы · месяц",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.text(expenses,"RUB"),fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
