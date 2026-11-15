package com.mamoro.karman

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

private val Violet = Color(0xFF8C4DFF)
private val SoftViolet = Color(0xFFA46BFF)
private val Mint = Color(0xFF48DBAB)

/** Static app backdrop; static drawing keeps tab switching cheap on AMOLED displays. */
@Composable
fun PremiumAppBackground(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF080C1C)
    val quality = LocalPremiumGraphics.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = if (dark) listOf(Color(0xFF090B1E), Color(0xFF12132C), Color(0xFF080B1D))
                   else listOf(Color(0xFFF5F2FF), Color(0xFFECE8FD), Color(0xFFF8F5FF))
        drawRect(Brush.verticalGradient(base))
        if (quality != "FAST") {
            val top = Offset(w * .98f, h * .08f)
            drawCircle(
                Brush.radialGradient(listOf(
                    if (dark) Color(0xFF7242E2).copy(alpha=.18f) else Color(0xFFBA98FF).copy(alpha=.26f),
                    Color.Transparent), center=top, radius=w * 1.15f),
                radius=w * 1.15f, center=top)
            if (quality == "MAX") {
                val bottom = Offset(w * .08f, h * .72f)
                drawCircle(Brush.radialGradient(listOf(
                    if (dark) Color(0xFF6C3ACB).copy(alpha=.11f) else Color(0xFFBDA7FF).copy(alpha=.19f),
                    Color.Transparent), center=bottom, radius=w * 1.08f),
                    radius=w * 1.08f, center=bottom)
            }
        }
    }
}

/** Soft bloom, diagonal glass ribbons and moving light remain BELOW text and hit targets. */
@Composable
private fun IncomeGlassLights(dark: Boolean, quality: String, modifier: Modifier = Modifier) {
    val profile = remember(quality) { GlassFxPolicy.forMode(quality) }
    // Animation state is read only by Canvas.draw, not by the card's text/layout.
    val shimmer = if (profile.animatedShimmer) {
        val transition = rememberInfiniteTransition(label = "income-glass-light")
        transition.animateFloat(
            initialValue = 0.06f, targetValue = 0.94f,
            animationSpec = infiniteRepeatable(
                animation = tween(profile.shimmerDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse), label = "glass-shimmer-position")
    } else null
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(
            if (dark) listOf(Color(0xFF462366), Color(0xFF311F5B), Color(0xFF191631))
            else listOf(Color(0xFFE1D2FF), Color(0xFFEBDEFF), Color(0xFFF4E9FF)),
            start=Offset(0f, 0f),end=Offset(w, h)))

        // Wide diagonal translucent ornaments match the original render.
        val leftRibbon = Path().apply {
            moveTo(-w * .13f, h * .02f)
            lineTo(w * .44f, 0f)
            lineTo(w * .98f, h * .67f)
            lineTo(w * .60f, h * .60f)
            close()
        }
        drawPath(leftRibbon, Brush.linearGradient(listOf(
            if (dark) Color(0xFFD9A5FF).copy(alpha=.12f) else Color.White.copy(alpha=.30f),
            Color.Transparent),start=Offset(0f,0f),end=Offset(w,h)))
        val rightRibbon = Path().apply {
            moveTo(w * .78f, -h * .16f)
            lineTo(w * 1.12f, h * .20f)
            lineTo(w * .93f, h * .84f)
            lineTo(w * .70f, h * .15f)
            close()
        }
        drawPath(rightRibbon, Brush.linearGradient(listOf(
            Color.White.copy(alpha=if(dark) .06f else .21f), Color.Transparent),
            start=Offset(w * .6f,0f),end=Offset(w,h)))

        // MAX: two soft moving blooms; BALANCED: one less frequent bloom; FAST: no animation.
        if (profile.glowLayers > 0) {
            val phase = shimmer?.value ?: .45f
            val position = Offset(w * (.10f + phase * .76f), h * .18f)
            drawCircle(
                brush=Brush.radialGradient(listOf(
                    if (dark) Color(0xFFC66AFF).copy(alpha=.35f)
                    else Color(0xFFBD70FF).copy(alpha=.24f),
                    if(dark) Color(0xFF9E4CF6).copy(alpha=.12f) else Color(0xFFEAD3FF).copy(alpha=.12f),
                    Color.Transparent), center=position, radius=w * .66f),
                radius=w * .66f, center=position)
            if (profile.glowLayers > 1) {
                val secondary = Offset(w * (1.0f - phase * .42f), h * .86f)
                drawCircle(Brush.radialGradient(listOf(
                    Color(0xFFB55EFF).copy(alpha=if(dark) .18f else .13f),
                    Color.Transparent), center=secondary,radius=w * .43f),
                    center=secondary,radius=w * .43f)
                rotate(-23f, pivot=Offset(w *.5f,h *.5f)) {
                    drawRect(
                        brush=Brush.horizontalGradient(listOf(
                            Color.Transparent,
                            Color.White.copy(alpha=if(dark) .07f else .12f),
                            Color.Transparent),
                            startX=w * phase - w * .18f, endX=w * phase + w * .30f),
                        topLeft=Offset(0f, 0f), size=size)
                }
            }
        } else {
            // Keep a static highlight even in performance mode.
            val staticSpot = Offset(w * .34f, h * .05f)
            drawCircle(Brush.radialGradient(listOf(
                if(dark) Color(0xFFB976FF).copy(alpha=.10f)
                else Color.White.copy(alpha=.28f),Color.Transparent),
                center=staticSpot,radius=w * .60f),center=staticSpot,radius=w * .60f)
        }
    }
}

/** Decoration and animations are selected in Settings: MAX, BALANCED or FAST. */
@Composable
fun PremiumGlassPanel(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background == Color(0xFF080C1C)
    val quality = LocalPremiumGraphics.current
    val profile = remember(quality) { GlassFxPolicy.forMode(quality) }
    val corners = RoundedCornerShape(22.dp)
    val panelGradient = if (dark)
        listOf(Color(0xFF242944), Color(0xFF181A31), Color(0xFF15172D))
    else listOf(Color(0xFFFCFAFF), Color(0xFFF1EDFF), Color(0xFFECE7FD))
    val stroke = Brush.linearGradient(if (dark)
        listOf(Color(0xFFD9ABFF).copy(alpha=if(prominent) .72f else .22f),
            Color(0xFFBBA4FF).copy(alpha=.10f), Color(0xFFC46CFF).copy(alpha=if(prominent) .47f else .14f))
        else listOf(Color.White.copy(alpha=.97f),Color(0xFFE4D7FF).copy(alpha=.30f),
            Color(0xFFB798EC).copy(alpha=if(prominent) .63f else .30f)))
    Box(modifier
        .then(if(profile.elevationDp > 0) Modifier.shadow(
            profile.elevationDp.dp, corners,
            ambientColor=Violet.copy(alpha=.36f),spotColor=Violet.copy(alpha=.32f)) else Modifier)
        .clip(corners)
        .background(if(quality == "FAST") colors.surface else Color.Transparent)
        .border(1.dp, stroke, corners)) {
        if (prominent) {
            IncomeGlassLights(dark,quality,Modifier.matchParentSize())
        } else if (quality != "FAST") {
            Box(Modifier.matchParentSize().background(
                Brush.linearGradient(panelGradient)))
        }
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp),content=content)
        }
    }
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
            Text("Кошелёк", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleLarge,
                color=MaterialTheme.colorScheme.onBackground)
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
                    color=MaterialTheme.colorScheme.onSurface,
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
            Text(title,style=MaterialTheme.typography.labelSmall,maxLines=1,
                color=MaterialTheme.colorScheme.onSurface)
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
