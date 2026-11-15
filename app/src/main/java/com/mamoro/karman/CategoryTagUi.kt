package com.mamoro.karman

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun walletCategoryColor(hex:String)=Color(android.graphics.Color.parseColor(hex))
private fun swatch(hex:String)=walletCategoryColor(hex)

fun walletCategoryIcon(key:String):ImageVector = WalletIconCatalog.vector(key)

/** User-owned categories are separate from transaction labels; deletion never discards history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTagManager(s:Snapshot,vm:KarmanViewModel,onBack:()->Unit) {
    var section by remember { mutableStateOf("categories") }
    var kind by remember { mutableStateOf(Kind.EXPENSE) }
    var editingCategory by remember { mutableStateOf<WalletCategory?>(null) }
    var editingTag by remember { mutableStateOf<WalletTag?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<WalletCategory?>(null) }
    var deletingTag by remember { mutableStateOf<WalletTag?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(Icons.Default.ArrowBack,"Назад")}
            Text("Категории и теги",style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=18.dp)) {
            listOf("categories" to "Категории","tags" to "Теги").forEachIndexed { index,(code,label)->
                SegmentedButton(selected=section==code,
                    onClick={section=code},shape=SegmentedButtonDefaults.itemShape(index,2)) {Text(label)}
            }
        }
        if(section=="categories") {
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=kind==Kind.EXPENSE,onClick={kind=Kind.EXPENSE},label={Text("Расходы")})
                FilterChip(selected=kind==Kind.INCOME,onClick={kind=Kind.INCOME},label={Text("Доходы")})
            }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=6.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(section=="categories") {
                items(s.categories.filter {it.kind==kind}, key={"cat-${it.id}"}) {c->
                    TaxonomyRow(c.name,c.colorHex,walletCategoryIcon(c.iconKey),
                        onEdit={editingCategory=c},onDelete={deletingCategory=c})
                }
            } else {
                if(s.tags.isEmpty()) item {Text("Тегов пока нет. Создайте первый.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)}
                items(s.tags,key={"tag-${it.id}"}) {tag->
                    TaxonomyRow("#${tag.name}",tag.colorHex,Icons.Default.Label,
                        onEdit={editingTag=tag},onDelete={deletingTag=tag})
                }
            }
            item {Spacer(Modifier.height(12.dp))}
        }
        Button(onClick={if(section=="categories") newCategory=true else newTag=true},
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)) {
            Icon(Icons.Default.Add,null)
            Spacer(Modifier.width(8.dp))
            Text(if(section=="categories")"Добавить категорию" else "Добавить тег")
        }
    }
    if(newCategory || editingCategory!=null) CategoryEditorDialog(
        original=editingCategory,kind=editingCategory?.kind?:kind,
        onDismiss={newCategory=false;editingCategory=null},
        onSave={name,color,icon->
            vm.saveCategory(editingCategory?.id,name,editingCategory?.kind?:kind,color,icon)
            newCategory=false;editingCategory=null
        })
    if(newTag || editingTag!=null) TagEditorDialog(
        original=editingTag,onDismiss={newTag=false;editingTag=null},
        onSave={name,color->vm.saveTag(editingTag?.id,name,color);newTag=false;editingTag=null})
    deletingCategory?.let{c->AlertDialog(onDismissRequest={deletingCategory=null},
        title={Text("Удалить категорию?")},
        text={Text("«${c.name}» исчезнет из списка выбора. Операции, шаблоны и история останутся без изменений.")},
        confirmButton={TextButton(onClick={vm.deleteCategory(c.id);deletingCategory=null}) {Text("Удалить")}},
        dismissButton={TextButton(onClick={deletingCategory=null}) {Text("Отмена")}})}
    deletingTag?.let{tag->AlertDialog(onDismissRequest={deletingTag=null},
        title={Text("Удалить тег?")},
        text={Text("«#${tag.name}» исчезнет из всех операций, но сами операции сохранятся.")},
        confirmButton={TextButton(onClick={vm.deleteTag(tag.id);deletingTag=null}) {Text("Удалить")}},
        dismissButton={TextButton(onClick={deletingTag=null}) {Text("Отмена")}})}
}

@Composable
private fun TaxonomyRow(name:String,colorHex:String,icon:ImageVector,
    onEdit:()->Unit,onDelete:()->Unit) {
    PremiumGlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(swatch(colorHex).copy(alpha=.20f),CircleShape),
                contentAlignment=Alignment.Center) {
                Icon(icon,null,tint=swatch(colorHex),modifier=Modifier.size(20.dp))
            }
            Text(name,modifier=Modifier.weight(1f).padding(start=10.dp),maxLines=1)
            IconButton(onClick=onEdit) {Icon(Icons.Default.Edit,"Изменить")}
            IconButton(onClick=onDelete) {Icon(Icons.Default.DeleteOutline,"Удалить",
                tint=MaterialTheme.colorScheme.error)}
        }
    }
}

private fun hexFromHsv(hue:Float,saturation:Float,brightness:Float):String {
    val rgb=android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness))
    return String.format(Locale.ROOT,"#%06X",rgb and 0xFFFFFF)
}

/** Full RGB picker, 36 favorites, hue/saturation/brightness and a typed HEX code. */
@Composable
private fun ColorPicker(selected:String,onSelect:(String)->Unit) {
    val hsv=remember(selected) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(selected),it) }
    }
    var hexInput by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { hexInput=selected }
    val inputValid=runCatching { CategoryTagRules.color(hexInput) }.isSuccess
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Готовые цвета",style=MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(vertical=4.dp)) {
            items(CategoryTagRules.colors,key={it}) {hex->
                val chosen=hex==selected
                Box(Modifier.size(37.dp).clip(CircleShape)
                    .background(swatch(hex)).clickable { onSelect(hex) },
                    contentAlignment=Alignment.Center) {
                    if(chosen) Icon(Icons.Default.Check,"Выбранный цвет",
                        tint=if(swatch(hex).luminance()>.48f)Color.Black else Color.White,
                        modifier=Modifier.size(21.dp))
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(swatch(selected),RoundedCornerShape(12.dp)))
            OutlinedTextField(value=hexInput,onValueChange={ input->
                if(input.length<=7) {
                    hexInput=input
                    runCatching {CategoryTagRules.color(input)}.getOrNull()?.let(onSelect)
                }
            },label={Text("HEX-цвет")},isError=!inputValid,
                singleLine=true,modifier=Modifier.weight(1f),supportingText={Text("Например, #42B7FF")})
        }
        Text("Оттенок",style=MaterialTheme.typography.labelSmall)
        Slider(value=hsv[0],onValueChange={onSelect(hexFromHsv(it,hsv[1],hsv[2]))},valueRange=0f..360f)
        Text("Насыщенность",style=MaterialTheme.typography.labelSmall)
        Slider(value=hsv[1],onValueChange={onSelect(hexFromHsv(hsv[0],it,hsv[2]))},valueRange=0f..1f)
        Text("Яркость",style=MaterialTheme.typography.labelSmall)
        Slider(value=hsv[2],onValueChange={onSelect(hexFromHsv(hsv[0],hsv[1],it))},valueRange=0f..1f)
    }
}

@Composable
private fun CategoryIconPicker(selected:String,onSelect:(String)->Unit) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Все") }
    val filtered=remember(search,group) {
        val term=search.trim()
        WalletIconCatalog.options.filter { option ->
            (group=="Все" || option.group==group) &&
                (term.isEmpty() || option.title.contains(term,ignoreCase=true) ||
                    option.key.contains(term,ignoreCase=true) ||
                    option.group.contains(term,ignoreCase=true))
        }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value=search,onValueChange={search=it},singleLine=true,
            leadingIcon={Icon(Icons.Default.Search,null)},label={Text("Поиск значка")},
            modifier=Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(WalletIconCatalog.groups,key={it}) {item->
                FilterChip(selected=group==item,onClick={group=item},label={Text(item)})
            }
        }
        Text("${filtered.size} значков",style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(columns=GridCells.Adaptive(72.dp),modifier=Modifier.fillMaxWidth().weight(1f),
            contentPadding=PaddingValues(vertical=6.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            gridItems(filtered,key={it.key}) { option ->
                val chosen=selected==option.key
                val tint=if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Surface(onClick={onSelect(option.key)},shape=RoundedCornerShape(13.dp),
                    color=if(chosen)MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.40f)) {
                    Column(Modifier.fillMaxWidth().padding(5.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(walletCategoryIcon(option.key),option.title,
                            tint=tint,modifier=Modifier.size(26.dp))
                        Text(option.title,maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if(filtered.isEmpty()) Text("Ничего не найдено",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A spacious dialog prevents hundreds of icons from expanding the entire operation form. */
@Composable
fun CategoryEditorDialog(original:WalletCategory?,kind:String,onDismiss:()->Unit,
    onSave:(String,String,String)->Unit) {
    var name by remember(original?.id) {mutableStateOf(original?.name?:"")}
    var color by remember(original?.id) {mutableStateOf(original?.colorHex?:"#A773FF")}
    var icon by remember(original?.id) {mutableStateOf(original?.iconKey?:"ShoppingCart")}
    var page by remember(original?.id) {mutableIntStateOf(0)}
    var error by remember(original?.id) {mutableStateOf<String?>(null)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxWidth().fillMaxHeight(.86f).padding(horizontal=16.dp),
            shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface,
            tonalElevation=8.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(original==null)"Новая категория" else "Изменить категорию",
                        style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    Box(Modifier.size(40.dp).background(swatch(color).copy(alpha=.15f),CircleShape),
                        contentAlignment=Alignment.Center) {
                        Icon(walletCategoryIcon(icon),"Текущий значок",tint=swatch(color))
                    }
                }
                OutlinedTextField(value=name,onValueChange={name=it;error=null},singleLine=true,
                    label={Text("Название категории")},modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=page==0,onClick={page=0},label={Text("Цвет")},
                        leadingIcon={Icon(Icons.Default.Palette,null,modifier=Modifier.size(18.dp))})
                    FilterChip(selected=page==1,onClick={page=1},label={Text("Значок")},
                        leadingIcon={Icon(Icons.Default.Category,null,modifier=Modifier.size(18.dp))})
                }
                if(page==0) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        ColorPicker(color,{color=it;error=null})
                    }
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        CategoryIconPicker(icon,{icon=it;error=null})
                    }
                }
                if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) {Text("Отмена")}
                    Button(onClick={
                        try { onSave(CategoryTagRules.name(name),CategoryTagRules.color(color),
                            CategoryTagRules.icon(icon)) }
                        catch(ex:Exception) { error=ex.message }
                    }) {Text("Сохранить")}
                }
            }
        }
    }
}

@Composable
fun TagEditorDialog(original:WalletTag?,onDismiss:()->Unit,onSave:(String,String)->Unit) {
    var name by remember(original?.id) {mutableStateOf(original?.name?:"")}
    var color by remember(original?.id) {mutableStateOf(original?.colorHex?:"#A773FF")}
    var error by remember(original?.id) {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(original==null)"Новый тег" else "Изменить тег")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value=name,onValueChange={name=it},singleLine=true,
                label={Text("Название тега")},modifier=Modifier.fillMaxWidth())
            ColorPicker(color,{color=it})
            if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(onClick={
            try { onSave(CategoryTagRules.name(name),CategoryTagRules.color(color)) }
            catch(ex:Exception){error=ex.message}
        }) {Text("Сохранить")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipSelector(tags:List<WalletTag>,selected:List<Long>,onToggle:(Long)->Unit) {
    if(tags.isEmpty()) Text("Тегов пока нет. Создайте их в разделе «Ещё» → «Категории и теги».",
        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        tags.forEach {tag->
            FilterChip(selected=tag.id in selected,onClick={onToggle(tag.id)},
                leadingIcon={Icon(Icons.Default.Label,null,tint=swatch(tag.colorHex),modifier=Modifier.size(16.dp))},
                label={Text("#${tag.name}")})
        }
    }
}
