package com.mamoro.karman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val Accent=Color(0xFF8C4DFF)
private val Green=Color(0xFF48DBAB)
val LocalPremiumGraphics = androidx.compose.runtime.compositionLocalOf { "BALANCED" }

@Composable
fun KarmanTheme(mode:String, graphicsMode:String, content: @Composable () -> Unit) {
    val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
    val dark=when(mode) { "LIGHT"->false; "SYSTEM"->systemDark; else->true }
    val scheme=if(dark) darkColorScheme(
        primary=Color(0xFFA773FF),onPrimary=Color(0xFF0A0C1A),
        background=Color(0xFF080C1C),onBackground=Color(0xFFF7F5FF),
        surface=Color(0xFF171B34),onSurface=Color(0xFFF7F5FF),
        surfaceVariant=Color(0xFF292949),onSurfaceVariant=Color(0xFFC7C1E2),
        error=Color(0xFFFF858E),outline=Color(0xFF544877)
    ) else lightColorScheme(
        primary=Color(0xFF7737DE),onPrimary=Color.White,
        background=Color(0xFFF0EEFF),onBackground=Color(0xFF15132C),
        surface=Color(0xFFFCFAFF),onSurface=Color(0xFF15132C),
        surfaceVariant=Color(0xFFE7E2FB),onSurfaceVariant=Color(0xFF554F70),
        error=Color(0xFFC63249),outline=Color(0xFFCCC2E2)
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalPremiumGraphics provides graphicsMode) {
        MaterialTheme(colorScheme=scheme, typography=Typography(),content=content)
    }
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(16,18,20)
        window.navigationBarColor=android.graphics.Color.rgb(16,18,20)
        setContent {
            val vm: KarmanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val snapshot by vm.snapshot.collectAsState()
            val dark = snapshot.settings.themeMode == "DARK" ||
                (snapshot.settings.themeMode == "SYSTEM" &&
                    androidx.compose.foundation.isSystemInDarkTheme())
            SideEffect {
                window.statusBarColor = if(dark) android.graphics.Color.rgb(8,12,28)
                    else android.graphics.Color.rgb(240,238,255)
                window.navigationBarColor = if(dark) android.graphics.Color.rgb(8,12,28)
                    else android.graphics.Color.rgb(240,238,255)
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if(dark) 0 else
                    (android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            }
            KarmanTheme(snapshot.settings.themeMode,snapshot.settings.graphicsMode) { KarmanApp(vm) }
        }
    }
}

private val tabs=listOf("Главная","История","План","Счета","Ещё")
private val tabIcons=listOf(Icons.Default.Home,Icons.Default.ReceiptLong,Icons.Default.CalendarMonth,
    Icons.Default.AccountBalanceWallet,Icons.Default.MoreHoriz)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KarmanApp(vm:KarmanViewModel=viewModel()) {
    val s by vm.snapshot.collectAsState()
    val issue by vm.issue.collectAsState()
    // Refresh date-driven values when the app returns to foreground after midnight.
    var currentDay by remember { mutableStateOf(LocalDate.now()) }
    val pager=rememberPagerState(pageCount={tabs.size})
    var quick by remember { mutableStateOf<Template?>(null) }
    var quickKind by remember { mutableStateOf(Kind.EXPENSE) }
    var showQuick by remember { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentDay=LocalDate.now()
        vm.refreshScheduled()
    }
    LaunchedEffect(issue) { if(issue!=null) { snackbar.showSnackbar(issue?:"Ошибка"); vm.dismissIssue() } }
    BackHandler(enabled=true) {
        when { showQuick->{showQuick=false;quick=null}
            pager.currentPage!=0->pager.requestScrollToPage(0)
            else -> Unit // Never exit when already on root.
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    PremiumAppBackground(Modifier.matchParentSize())
    Scaffold(
        containerColor=Color.Transparent,
        snackbarHost={SnackbarHost(snackbar)},
        floatingActionButton={
            // Home already has four prominent actions; avoid covering the Goals card.
            if(pager.currentPage in 1..2) FloatingActionButton(
                onClick={quick=null;quickKind=Kind.EXPENSE;showQuick=true},
                containerColor=MaterialTheme.colorScheme.primary, contentColor=MaterialTheme.colorScheme.onPrimary,
                shape=RoundedCornerShape(18.dp)) {Icon(Icons.Default.Add,"Новая операция")}
        },
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { i,name ->
                NavigationBarItem(selected=pager.currentPage==i,
                    onClick={pager.requestScrollToPage(i)},
                    label={Text(name,maxLines=1)},
                    icon={Icon(tabIcons[i],contentDescription=name)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.onPrimary,selectedTextColor=MaterialTheme.colorScheme.primary,
                        indicatorColor=MaterialTheme.colorScheme.primary,
                        unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }}
    ) {padding ->
        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount=0) {page ->
            when(page) {
                0->HomeTab(s,vm,currentDay,onQuick={ template->
                    if(template.amountMinor>0) vm.income(template.kind,template.accountId,template.amountMinor,template.category,"")
                    else {quick=template;showQuick=true}
                },onNavigate={pager.requestScrollToPage(it)},onNewOperation={kind ->
                    quick=null;quickKind=kind;showQuick=true
                })
                1->HistoryTab(s,vm)
                2->PlansTab(s,vm)
                3->AccountsTab(s,vm)
                else->SettingsTab(s,vm)
            }
        }
    }
    if(showQuick) QuickEntrySheet(s,vm,quick,defaultKind=quickKind,onClose={showQuick=false;quick=null})
    }
}

@Composable
fun TitleBar(title:String,subtitle:String?=null) {
    Column(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun KCard(modifier:Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(17.dp),content=content) }
}
@Composable
fun KTitle(text:String,modifier:Modifier=Modifier) { Text(text,modifier,color=MaterialTheme.colorScheme.onSurfaceVariant,
    style=MaterialTheme.typography.labelLarge) }
@Composable
fun MoneyLine(amount:Long,currency:String,large:Boolean=false,color:Color?=null) {
    Text(Money.text(amount,currency),color=color?:MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,
        style=if(large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
}
@Composable
fun EmptyText(text:String) { Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=22.dp)) }

@Composable
fun HomeTab(s:Snapshot,vm:KarmanViewModel,today:LocalDate,onQuick:(Template)->Unit,
            onNavigate:(Int)->Unit,onNewOperation:(String)->Unit) {
    PremiumHomeTab(s,vm,today,onQuick,onNavigate,onNewOperation)
}

@Composable
fun TransactionRow(e:Entry,s:Snapshot,modifier:Modifier=Modifier, trailing: (@Composable () -> Unit)?=null) {
    val incoming=e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
    val account=s.accounts.find{it.id==e.fromAccount}
    KCard(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) {
                Icon(when(e.kind){Kind.TRANSFER->Icons.Default.SwapHoriz;Kind.BUY,Kind.SELL->Icons.Default.TrendingUp;
                    Kind.COUPON->Icons.Default.Payments;else->if(incoming)Icons.Default.ArrowDownward else Icons.Default.ArrowUpward},
                    null,tint=if(incoming) Green else Accent)
            }
            Column(Modifier.weight(1f)) {
                Text(e.category.ifEmpty {if(incoming) "Доход" else "Расход"},fontWeight=FontWeight.Medium)
                Text("${e.day} · ${account?.name?:"Счёт"}${if(e.note.isNotBlank()) " · ${e.note}" else ""}",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("${if(incoming)"+" else "−"}${Money.text(e.amountMinor,account?.currency?:"RUB")}",
                    color=if(incoming)Green else MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                if(account?.currency!="RUB") Text(Money.text(e.baseMinor,"RUB"),color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelSmall)
            }
            if(trailing!=null) trailing()
        }
    }
}

@Composable
fun HistoryTab(s:Snapshot,vm:KarmanViewModel) {
    var filter by remember {mutableStateOf("ALL")}
    var chosen by remember {mutableStateOf<Entry?>(null)}
    var editing by remember {mutableStateOf(false)}
    var pendingDelete by remember {mutableStateOf<Entry?>(null)}
    var editedCategory by remember {mutableStateOf("")}
    var editedNote by remember {mutableStateOf("")}
    var editedDate by remember {mutableStateOf("")}
    var editError by remember {mutableStateOf<String?>(null)}
    var category by remember {mutableStateOf<String?>(null)}
    var search by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("ALL") }
    val rows=remember(s,filter,category,search,period) {
        val month=when(period) {
            "CURRENT" -> YearMonth.now().toString()
            "PREVIOUS" -> YearMonth.now().minusMonths(1).toString()
            else -> null
        }
        val needle=search.trim()
        val accountNames=s.accounts.associate { it.id to it.name }
        s.entries.filter { e ->
            (filter=="ALL" || filter=="IN"&&e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
              ||filter=="OUT"&&e.kind in listOf(Kind.EXPENSE,Kind.BUY)) &&
                (category==null || e.category==category) &&
                (month==null || e.day.startsWith(month)) &&
                (needle.isEmpty() || e.category.contains(needle,ignoreCase=true) ||
                  e.note.contains(needle,ignoreCase=true) ||
                  e.day.contains(needle,ignoreCase=true) ||
                  (accountNames[e.fromAccount]?.contains(needle,ignoreCase=true)==true))
        }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { TitleBar("История","Операции и аналитика") }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Все","OUT" to "Расходы","IN" to "Доходы").forEach{ (k,v)->
                FilterChip(selected=filter==k,onClick={filter=k;category=null},label={Text(v)})
            }
        }}
        item {
            OutlinedTextField(value=search,onValueChange={search=it},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Поиск по операциям")},
                leadingIcon={Icon(Icons.Default.Search,contentDescription=null)})
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(listOf("ALL" to "Всё время","CURRENT" to "Этот месяц","PREVIOUS" to "Прошлый месяц")) { (key,label) ->
                    FilterChip(selected=period==key,onClick={period=key},label={Text(label)})
                }
            }
        }
        if(search.isNotBlank() || period!="ALL") item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Найдено: ${rows.size}",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                TextButton(onClick={search="";period="ALL"}) {Text("Сбросить")}
            }
        }
        if(filter=="OUT") {
            item {
                val monthly=s.entries.filter{it.kind==Kind.EXPENSE&&YearMonth.from(LocalDate.parse(it.day))==YearMonth.now()}
                val groups=monthly.groupBy{it.category}.mapValues {it.value.sumOf {e->e.baseMinor}}.toList().sortedByDescending{it.second}
                KCard {
                    Text("Расходы по категориям · ${YearMonth.now()}",fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    if(groups.isEmpty()) EmptyText("Нет расходов за текущий месяц")
                    groups.take(8).forEach { (name,total)->
                        val max=groups.maxOf{it.second}.coerceAtLeast(1L)
                        Column(Modifier.fillMaxWidth().clickable {category=if(category==name)null else name}.padding(vertical=6.dp)) {
                            Row { Text(name,modifier=Modifier.weight(1f));Text(Money.text(total,"RUB")) }
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(progress={total.toFloat()/max},modifier=Modifier.fillMaxWidth(),
                                color=Accent,trackColor=MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    if(category!=null) TextButton(onClick={category=null}) {Text("Сбросить категорию")}
                }
            }
        }
        if(rows.isEmpty()) item {EmptyText("Записей не найдено")}
        items(rows,key={it.id}) {e->
            TransactionRow(e,s,Modifier.clickable{chosen=e;editing=false;editedCategory=e.category;editedNote=e.note;editedDate=e.day;editError=null})
        }
    }
    if(chosen!=null && !editing) AlertDialog(onDismissRequest={chosen=null},
        title={Text(chosen?.category?:"Операция")},
        text={Text("${chosen?.day}\n${chosen?.note.orEmpty()}")},
        confirmButton={
            Row {
                if(chosen?.kind in listOf(Kind.EXPENSE,Kind.INCOME)&&chosen?.sourceKey==null)
                    TextButton(onClick={editing=true}) {Text("Изменить")}
                if(chosen?.kind in listOf(Kind.EXPENSE,Kind.INCOME,Kind.TRANSFER)&&chosen?.sourceKey==null)
                    TextButton(onClick={pendingDelete=chosen;chosen=null}) {Text("Удалить",color=MaterialTheme.colorScheme.error)}
            }
        },dismissButton={TextButton(onClick={chosen=null}){Text("Закрыть")}})
    if(pendingDelete!=null) AlertDialog(onDismissRequest={pendingDelete=null},
        title={Text("Удалить операцию?")},
        text={Text("Запись исчезнет из истории, а баланс будет пересчитан.")},
        confirmButton={TextButton(onClick={pendingDelete?.let{vm.deleteEntry(it.id)};pendingDelete=null}) {
            Text("Удалить",color=MaterialTheme.colorScheme.error)
        }},dismissButton={TextButton(onClick={pendingDelete=null}){Text("Отмена")}})
    if(chosen!=null && editing) ActionDialog("Изменить сведения",onClose={chosen=null;editing=false},submit={
        try {
            vm.editEntryDetails(requireNotNull(chosen).id,editedCategory,editedNote,LocalDate.parse(editedDate))
            chosen=null;editing=false
        } catch(ex:Exception) {editError=ex.message?:"Ошибка"}
    }) {
        SimpleText("Категория",editedCategory,{editedCategory=it})
        SimpleText("Комментарий",editedNote,{editedNote=it})
        SimpleText("Дата (ГГГГ-ММ-ДД)",editedDate,{editedDate=it})
        if(editError!=null) Text(editError.orEmpty(),color=MaterialTheme.colorScheme.error)
    }
}

private val expenses=listOf("Продукты","Транспорт","Дом","Здоровье","Кафе","Покупки","Связь","Платежи","Другое")
private val incomes=listOf("Зарплата","Подработка","Подарки","Возврат","Другое")

private fun typing(text:String):String {
    val cleaned=text.filter {it.isDigit()||it==','||it=='.'}.replace(',','.')
    val pos=cleaned.indexOf('.')
    val whole=(if(pos>=0)cleaned.substring(0,pos) else cleaned).trimStart('0').ifEmpty {"0"}.take(13)
    val formatted=whole.reversed().chunked(3).joinToString(" ").reversed()
    return if(pos>=0) formatted+"."+cleaned.substring(pos+1).filter{it.isDigit()}.take(2) else formatted
}
private fun moneyRaw(value:Long):String = if(value==0L) "" else java.math.BigDecimal.valueOf(value,2).toPlainString()
@Composable
fun AmountInput(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange={onValue(typing(it))},label={Text(label)},
        singleLine=true,modifier=modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
}
@Composable
fun SimpleText(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},singleLine=true,modifier=modifier.fillMaxWidth())
}
@Composable
fun AccountChoices(accounts:List<Account>,selected:Long,onChange:(Long)->Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
        modifier=Modifier.fillMaxWidth()) {
        items(accounts,key={it.id}) { a->
            FilterChip(selected=selected==a.id,onClick={onChange(a.id)},label={Text("${a.name} · ${a.currency}")})
        }
    }
}
@Composable
fun PrimaryAction(label:String,click:()->Unit,enabled:Boolean=true) {
    Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().height(52.dp),
        shape=RoundedCornerShape(15.dp)) { Text(label,fontWeight=FontWeight.SemiBold) }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickEntrySheet(s:Snapshot,vm:KarmanViewModel,template:Template?,
                    defaultKind:String=Kind.EXPENSE,onClose:()->Unit) {
    val lastExpense=s.entries.firstOrNull{it.kind==Kind.EXPENSE}
    val lastIncome=s.entries.firstOrNull{it.kind==Kind.INCOME}
    val initialKind=template?.kind?:defaultKind
    val recentForKind=if(initialKind==Kind.INCOME) lastIncome else lastExpense
    var kind by remember(template?.id,defaultKind){mutableStateOf(initialKind)}
    var amount by remember(template?.id){mutableStateOf(moneyRaw(template?.amountMinor?:0))}
    var accountId by remember(template?.id,defaultKind) {mutableLongStateOf(template?.accountId?:recentForKind?.fromAccount?:s.accounts.firstOrNull()?.id?:0)}
    var category by remember(template?.id,defaultKind) {
        mutableStateOf(template?.category?:recentForKind?.category?:if(initialKind==Kind.INCOME)"Зарплата" else "Продукты")
    }
    var note by remember {mutableStateOf("")}
    var selectedDay by remember {mutableStateOf(LocalDate.now().toString())}
    var extra by remember {mutableStateOf(false)}
    var saveTemplate by remember {mutableStateOf(false)}
    var templateName by remember {mutableStateOf("")}
    var noAmountTemplate by remember {mutableStateOf(false)}
    var toAccount by remember {mutableLongStateOf(s.accounts.firstOrNull{it.id!=accountId}?.id?:accountId)}
    var toAmount by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    val focusRequester=remember{FocusRequester()}
    LaunchedEffect(Unit){focusRequester.requestFocus()}
    ModalBottomSheet(onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.surface,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal=20.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(template!=null)template.title else "Новая операция",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Kind.EXPENSE to "Расход",Kind.INCOME to "Доход",Kind.TRANSFER to "Перевод").forEach{(k,name)->
                    FilterChip(selected=kind==k,onClick={
                        kind=k
                        val last=if(k==Kind.INCOME)lastIncome else lastExpense
                        category=last?.category?:if(k==Kind.INCOME)"Зарплата" else "Продукты"
                        if(last!=null) accountId=last.fromAccount
                        if(toAccount==accountId) toAccount=s.accounts.firstOrNull{it.id!=accountId}?.id?:accountId
                    },label={Text(name)})
                }
            }
            AmountInput("Сумма",amount,{amount=it},Modifier.focusRequester(focusRequester))
            Text("Счёт",color=MaterialTheme.colorScheme.onSurfaceVariant)
            AccountChoices(s.accounts,accountId,{newId ->
                accountId=newId
                if(toAccount==newId) toAccount=s.accounts.firstOrNull{it.id!=newId}?.id?:newId
            })
            if(kind==Kind.TRANSFER) {
                Text("На счёт",color=MaterialTheme.colorScheme.onSurfaceVariant)
                AccountChoices(s.accounts.filter{it.id!=accountId},toAccount,{toAccount=it})
                if(s.accounts.find{it.id==accountId}?.currency!=s.accounts.find{it.id==toAccount}?.currency)
                    AmountInput("Зачислится (необязательно)",toAmount,{toAmount=it})
            } else {
                Text("Категория",color=MaterialTheme.colorScheme.onSurfaceVariant)
                val all=(if(kind==Kind.EXPENSE) expenses else incomes)+
                    s.entries.filter{it.kind==kind}.map{it.category}
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),
                    verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    all.distinct().forEach { name->
                        FilterChip(selected=category==name,onClick={category=name},label={Text(name)})
                    }
                }
            }
            TextButton(onClick={extra=!extra}) {
                Icon(if(extra)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                Text(if(extra)"Скрыть детали" else "Дополнительно: дата, заметка, шаблон")
            }
            if(extra) {
                SimpleText("Дата (ГГГГ-ММ-ДД)",selectedDay,{selectedDay=it})
                SimpleText("Комментарий",note,{note=it})
                if(kind!=Kind.TRANSFER) SimpleText("Своя категория",category,{category=it})
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=saveTemplate,onCheckedChange={saveTemplate=it})
                    Text("Сохранить как шаблон")
                }
                if(saveTemplate) {
                    SimpleText("Название шаблона",templateName,{templateName=it})
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=noAmountTemplate,onCheckedChange={noAmountTemplate=it})
                        Text("Шаблон без суммы")
                    }
                }
            }
            if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
            PrimaryAction("Сохранить", click = {
                try {
                    val sum=Money.parse(amount);require(sum>0){"Введите сумму больше нуля"}
                    require(accountId!=0L)
                    val date=LocalDate.parse(selectedDay)
                    if(kind==Kind.TRANSFER) {
                        require(s.accounts.size>1&&toAccount!=accountId){"Добавьте второй счёт"}
                        vm.transfer(accountId,toAccount,sum,toAmount.takeIf{it.isNotBlank()}?.let(Money::parse),date)
                    } else {
                        require(category.isNotBlank())
                        vm.income(kind,accountId,sum,category,note,date)
                        if(saveTemplate) vm.addTemplate(templateName.ifBlank{category},kind,category,accountId,
                            if(noAmountTemplate)0 else sum)
                    }
                    onClose()
                } catch(e:Exception) {error=e.message?:"Проверьте введённые данные"}
            })
        }
    }
}
