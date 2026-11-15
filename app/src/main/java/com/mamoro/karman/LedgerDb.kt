package com.mamoro.karman

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private fun values(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    for ((k,v) in pairs) when(v) {
        null -> putNull(k)
        is String -> put(k,v)
        is Int -> put(k,v)
        is Long -> put(k,v)
        is Boolean -> put(k,if(v) 1 else 0)
        else -> error("Unsupported database value")
    }
}
private fun Cursor.str(k: String): String = getString(getColumnIndexOrThrow(k)) ?: ""
private fun Cursor.lng(k: String): Long = getLong(getColumnIndexOrThrow(k))
private fun Cursor.flag(k: String): Boolean = lng(k) == 1L
private fun Cursor.nullLong(k: String): Long? = getColumnIndexOrThrow(k).let { i -> if(isNull(i)) null else getLong(i) }

/** Brand-new schema: no old app database or package is accessed. */
class LedgerDb(ctx: Context): SQLiteOpenHelper(ctx.applicationContext, "karman_v1.db", null, 3) {
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, currency TEXT NOT NULL,
            opening_minor INTEGER NOT NULL, opening_base_minor INTEGER NOT NULL, rate_micros INTEGER NOT NULL,
            included INTEGER NOT NULL DEFAULT 1,
            bank_group TEXT NOT NULL DEFAULT '', card_last4 TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, kind TEXT NOT NULL,
            from_account INTEGER NOT NULL REFERENCES accounts(id), amount_minor INTEGER NOT NULL,
            base_minor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
            to_account INTEGER REFERENCES accounts(id), to_amount_minor INTEGER, source_key TEXT UNIQUE)""")
        db.execSQL("CREATE INDEX idx_entries_day ON entries(day DESC,id DESC)")
        db.execSQL("""CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
            target_minor INTEGER NOT NULL, saved_minor INTEGER NOT NULL, reserve INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount_minor INTEGER NOT NULL,
            account_id INTEGER NOT NULL REFERENCES accounts(id), due TEXT NOT NULL, frequency TEXT NOT NULL,
            auto INTEGER NOT NULL, reserve INTEGER NOT NULL, anchor_day INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL UNIQUE, limit_minor INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, kind TEXT NOT NULL,
            category TEXT NOT NULL, account_id INTEGER NOT NULL REFERENCES accounts(id), amount_minor INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE bonds(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, nominal_minor INTEGER NOT NULL,
            quote_minor INTEGER NOT NULL, coupon_bps INTEGER NOT NULL, account_id INTEGER NOT NULL REFERENCES accounts(id))""")
        db.execSQL("""CREATE TABLE lots(id INTEGER PRIMARY KEY AUTOINCREMENT, bond_id INTEGER NOT NULL REFERENCES bonds(id),
            quantity INTEGER NOT NULL, cost_minor INTEGER NOT NULL, day TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE coupons(id INTEGER PRIMARY KEY AUTOINCREMENT, bond_id INTEGER NOT NULL REFERENCES bonds(id),
            day TEXT NOT NULL, amount_minor INTEGER NOT NULL, posted INTEGER NOT NULL DEFAULT 0,
            UNIQUE(bond_id, day))""")
        db.execSQL("CREATE TABLE preferences(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createCategoryTagTables(db)
        seedCategoryCatalog(db)
        db.insertOrThrow("accounts",null,values("name" to "Наличные", "currency" to "RUB",
            "opening_minor" to 0L, "opening_base_minor" to 0L, "rate_micros" to 1000000L, "included" to 1))
    }
    private fun createCategoryTagTables(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL COLLATE NOCASE, kind TEXT NOT NULL,
            color_hex TEXT NOT NULL, icon_key TEXT NOT NULL,
            UNIQUE(kind,name))""")
        db.execSQL("""CREATE TABLE tags(id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL COLLATE NOCASE UNIQUE, color_hex TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE entry_tags(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
            tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
            PRIMARY KEY(entry_id,tag_id))""")
        db.execSQL("CREATE INDEX idx_entry_tags_tag ON entry_tags(tag_id)")
    }
    private fun seedCategoryCatalog(db:SQLiteDatabase) {
        val expense=listOf("Продукты" to "shopping","Транспорт" to "car","Дом" to "home",
            "Здоровье" to "health","Кафе" to "food","Покупки" to "shopping",
            "Связь" to "other","Платежи" to "work","Другое" to "other")
        val income=listOf("Зарплата" to "work","Подработка" to "work",
            "Подарки" to "gift","Возврат" to "other","Другое" to "other")
        (expense.map { Triple(Kind.EXPENSE,it.first,it.second) } +
            income.map { Triple(Kind.INCOME,it.first,it.second) }).forEach { (kind,name,icon) ->
            db.insertWithOnConflict("categories",null,values("name" to name,"kind" to kind,
                "color_hex" to "#A773FF","icon_key" to icon),SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        require(old in 1..2 && new==3) { "Missing migration from $old to $new" }
        if (old<2) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN bank_group TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE accounts ADD COLUMN card_last4 TEXT NOT NULL DEFAULT ''")
        }
        if (old<3) {
            createCategoryTagTables(db)
            seedCategoryCatalog(db)
        }
    }
    fun snapshot(): Snapshot {
        val db = readableDatabase
        fun <T> read(sql:String, transform:(Cursor)->T):List<T> {
            val list=ArrayList<T>();db.rawQuery(sql,null).use { c -> while(c.moveToNext()) list.add(transform(c)) }
            return list
        }
        val prefs = read("SELECT key,value FROM preferences") { it.str("key") to it.str("value") }.toMap()
        fun num(k:String,default:Long=0L)=prefs[k]?.toLongOrNull()?:default
        fun bool(k:String)=prefs[k]=="1"
        val settings = Settings(
            baseCurrency="RUB", displayCurrency=prefs["displayCurrency"]?.takeIf { it in listOf("RUB","EUR","USD","BYN") }?:"RUB",
            usdPerBaseMicros=num("usdPerBaseMicros",11000).coerceAtLeast(1),
            eurPerBaseMicros=num("eurPerBaseMicros",10000).coerceAtLeast(1),
            bynPerBaseMicros=num("bynPerBaseMicros",35000).coerceAtLeast(1),
            extraReserveMinor=num("extraReserveMinor"), sharedReserveMinor=num("sharedReserveMinor"),
            coveredPlansMinor=num("coveredPlansMinor"),includeInvestments=bool("includeInvestments"), seasonal=bool("seasonal"),
            nextIncomeDate=prefs["nextIncomeDate"]?.takeIf {
                runCatching { LocalDate.parse(it) }.isSuccess
            },
            themeMode=prefs["themeMode"]?.takeIf { it in setOf("DARK","LIGHT","SYSTEM") } ?: "DARK",
            graphicsMode=prefs["graphicsMode"]?.takeIf { it in setOf("MAX","BALANCED","FAST") } ?: "BALANCED",
            bankImportEnabled=bool("bankImportEnabled"),
            bankImportAccountId=prefs["bankImportAccountId"]?.toLongOrNull()?.takeIf { it > 0 } ?: 0L
        )
        val tagAssignments = read("""SELECT et.entry_id AS eid,t.name AS tag_name
            FROM entry_tags et JOIN tags t ON t.id=et.tag_id ORDER BY et.entry_id,t.name""") {
            it.lng("eid") to it.str("tag_name")
        }.groupBy({it.first},{it.second})
        return Snapshot(
            accounts=read("SELECT * FROM accounts ORDER BY id") { Account(it.lng("id"),it.str("name"),it.str("currency"),
                it.lng("opening_minor"),it.lng("opening_base_minor"),it.lng("rate_micros"),it.flag("included"),
                it.str("bank_group"),it.str("card_last4")) },
            entries=read("SELECT * FROM entries ORDER BY day DESC,id DESC") { Entry(it.lng("id"),it.str("day"),it.str("kind"),
                it.lng("from_account"),it.lng("amount_minor"),it.lng("base_minor"),it.str("category"),
                it.str("note"),it.nullLong("to_account"),it.nullLong("to_amount_minor"),
                it.getString(it.getColumnIndexOrThrow("source_key")),
                tagAssignments[it.lng("id")].orEmpty()) },
            goals=read("SELECT * FROM goals ORDER BY id") { Goal(it.lng("id"),it.str("title"),it.lng("target_minor"),it.lng("saved_minor"),it.flag("reserve")) },
            plans=read("SELECT * FROM plans ORDER BY due,id") { Plan(it.lng("id"),it.str("title"),it.lng("amount_minor"),
                it.lng("account_id"),it.str("due"),it.str("frequency"),it.flag("auto"),it.flag("reserve"),it.lng("anchor_day").toInt()) },
            budgets=read("SELECT * FROM budgets ORDER BY category") { Budget(it.lng("id"),it.str("category"),it.lng("limit_minor")) },
            templates=read("SELECT * FROM templates ORDER BY id DESC") { Template(it.lng("id"),it.str("title"),it.str("kind"),
                it.str("category"),it.lng("account_id"),it.lng("amount_minor")) },
            bonds=read("SELECT * FROM bonds ORDER BY id DESC") { Bond(it.lng("id"),it.str("name"),it.lng("nominal_minor"),
                it.lng("quote_minor"),it.lng("coupon_bps").toInt(),it.lng("account_id")) },
            lots=read("SELECT * FROM lots ORDER BY day,id") { Lot(it.lng("id"),it.lng("bond_id"),it.lng("quantity"),it.lng("cost_minor"),it.str("day")) },
            coupons=read("SELECT * FROM coupons ORDER BY day,id") { Coupon(it.lng("id"),it.lng("bond_id"),it.str("day"),it.lng("amount_minor"),it.flag("posted")) },
            settings=settings,
            categories=read("SELECT * FROM categories ORDER BY kind,name") {
                WalletCategory(it.lng("id"),it.str("name"),it.str("kind"),
                    it.str("color_hex"),it.str("icon_key")) },
            tags=read("SELECT * FROM tags ORDER BY name") {
                WalletTag(it.lng("id"),it.str("name"),it.str("color_hex")) }
        )
    }
    private inline fun <T> mutation(block:(SQLiteDatabase)->T):T {
        val db=writableDatabase
        db.beginTransaction()
        try { val value=block(db);db.setTransactionSuccessful();return value }
        finally { db.endTransaction() }
    }
    private fun insertEntry(db:SQLiteDatabase, kind:String, accountId:Long, local:Long, base:Long,
        category:String, note:String, day:String, destination:Long?=null, credited:Long?=null, source:String?=null):Long {
        require(local in 1..Money.MAX_MINOR && base in 0..Money.MAX_MINOR)
        require(category.length<=80 && note.length<=500)
        LocalDate.parse(day)
        return db.insertOrThrow("entries",null,values("day" to day,"kind" to kind,"from_account" to accountId,
            "amount_minor" to local,"base_minor" to base,"category" to category,"note" to note,
            "to_account" to destination,"to_amount_minor" to credited,"source_key" to source))
    }
    fun account(name:String,currency:String,opening:Long,bankGroup:String="",cardLast4:String=""):Long=mutation { db ->
        require(name.isNotBlank()&&name.length<=80&&currency in listOf("RUB","USD","EUR","BYN"))
        require(opening in 0..Money.MAX_MINOR)
        val current=snapshot()
        val bank=BankSubaccounts.cleanBank(bankGroup)
        BankSubaccounts.verifyMetadata(current.accounts,bank,cardLast4)
        val rate = FxRates.basePerNativeMicros(current.settings, currency)
        db.insertOrThrow("accounts",null,values("name" to name,"currency" to currency,"opening_minor" to opening,
            "opening_base_minor" to Money.toBase(opening,rate),"rate_micros" to rate,"included" to 1,
            "bank_group" to bank,"card_last4" to cardLast4))
    }
    fun includeAccount(id:Long,include:Boolean)=mutation { db ->
        require(db.update("accounts",values("included" to include),"id=?",arrayOf(id.toString()))==1)
    }
    fun editAccount(id:Long,name:String,currency:String,opening:Long,bankGroup:String="",cardLast4:String="") = mutation { db ->
        val s=snapshot()
        val old=s.accounts.firstOrNull { it.id==id } ?: error("Счёт не найден")
        require(name.isNotBlank() && name.length<=80 && currency in listOf("RUB","USD","EUR","BYN"))
        require(opening in 0..Money.MAX_MINOR)
        val bank=BankSubaccounts.cleanBank(bankGroup)
        BankSubaccounts.verifyMetadata(s.accounts,bank,cardLast4,id)
        if (AccountRules.hasLinkedData(s,id)) {
            require(currency==old.currency && opening==old.openingMinor) {
                "Счёт связан с операциями, планами или инвестициями: нельзя менять валюту и начальный остаток"
            }
        }
        val rate=if (currency==old.currency && opening==old.openingMinor) old.rateMicros
                 else FxRates.basePerNativeMicros(s.settings,currency)
        require(db.update("accounts",values("name" to name,"currency" to currency,
            "opening_minor" to opening,"opening_base_minor" to
                if (currency==old.currency && opening==old.openingMinor) old.openingBaseMinor else Money.toBase(opening,rate),
            "rate_micros" to rate,"bank_group" to bank,"card_last4" to cardLast4),
            "id=?",arrayOf(id.toString()))==1)
    }
    fun deleteAccount(id:Long) = mutation { db ->
        val s=snapshot()
        require(AccountRules.canDelete(s,id)) {
            "Нельзя удалить последний счёт или счёт с операциями, планами, шаблонами или инвестициями"
        }
        require(db.delete("accounts","id=?",arrayOf(id.toString()))==1) { "Счёт не найден" }
    }
    fun saveCategory(id:Long?,name:String,kind:String,colorHex:String,iconKey:String):Long=mutation { db ->
        val clean=CategoryTagRules.name(name)
        val type=CategoryTagRules.categoryKind(kind)
        val color=CategoryTagRules.color(colorHex)
        val icon=CategoryTagRules.icon(iconKey)
        if(id==null) {
            db.insertOrThrow("categories",null,values("name" to clean,"kind" to type,
                "color_hex" to color,"icon_key" to icon))
        } else {
            val previous=snapshot().categories.firstOrNull { it.id==id } ?: error("Категория не найдена")
            require(previous.kind==type) {"Для уже созданной категории нельзя менять тип доход/расход"}
            require(db.update("categories",values("name" to clean,"color_hex" to color,
                "icon_key" to icon),"id=?",arrayOf(id.toString()))==1)
            if(previous.name!=clean) {
                // A rename keeps history, templates and category-based budget sums aligned.
                db.update("entries",values("category" to clean),"kind=? AND category=?",
                    arrayOf(type,previous.name))
                if(type==Kind.EXPENSE) db.update("budgets",values("category" to clean),
                    "category=?",arrayOf(previous.name))
                db.update("templates",values("category" to clean),"kind=? AND category=?",
                    arrayOf(type,previous.name))
            }
            id
        }
    }
    fun deleteCategory(id:Long)=mutation { db ->
        // Historic operations keep their category labels and remain in analytics.
        require(db.delete("categories","id=?",arrayOf(id.toString()))==1) {"Категория не найдена"}
    }
    fun saveTag(id:Long?,name:String,colorHex:String):Long=mutation { db ->
        val clean=CategoryTagRules.name(name)
        val color=CategoryTagRules.color(colorHex)
        if(id==null) db.insertOrThrow("tags",null,values("name" to clean,"color_hex" to color))
        else {
            require(db.update("tags",values("name" to clean,"color_hex" to color),
                "id=?",arrayOf(id.toString()))==1) {"Тег не найден"}
            id
        }
    }
    fun deleteTag(id:Long)=mutation { db ->
        // ON DELETE CASCADE removes only the tag associations, not transactions.
        require(db.delete("tags","id=?",arrayOf(id.toString()))==1) {"Тег не найден"}
    }
    private fun assignTags(db:SQLiteDatabase,entryId:Long,ids:List<Long>) {
        val unique=CategoryTagRules.tagIds(ids)
        require(db.rawQuery("SELECT 1 FROM entries WHERE id=?",arrayOf(entryId.toString()))
            .use {it.moveToFirst()}) {"Операция не найдена"}
        if(unique.isNotEmpty()) {
            val available=mutableSetOf<Long>()
            db.rawQuery("SELECT id FROM tags",null).use { c -> while(c.moveToNext()) available.add(c.getLong(0)) }
            require(unique.all { it in available }) {"Некоторые теги не найдены"}
        }
        db.delete("entry_tags","entry_id=?",arrayOf(entryId.toString()))
        unique.forEach { tagId -> db.insertOrThrow("entry_tags",null,
            values("entry_id" to entryId,"tag_id" to tagId)) }
    }
    fun setEntryTags(entryId:Long,ids:List<Long>)=mutation { db -> assignTags(db,entryId,ids) }

    fun entry(kind:String,accountId:Long,amount:Long,category:String,note:String="",
        date:LocalDate=LocalDate.now(),toId:Long?=null,toAmount:Long?=null,
        tagIds:List<Long> = emptyList()):Long=mutation { db ->
        require(kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER))
        val current=snapshot()
        val accounts=current.accounts
        val a=accounts.first { it.id==accountId }
        val currentSettings=current.settings
        val base=FxRates.toBase(currentSettings,a.currency,amount)
        val entryId=if(kind==Kind.TRANSFER) {
            require(tagIds.isEmpty()) {"Теги назначаются только доходам и расходам"}
            val dest=accounts.first{it.id==toId};require(dest.id!=accountId)
            val credit=toAmount?:FxRates.fromBase(currentSettings,dest.currency,base)
            require(credit in 1..Money.MAX_MINOR)
            insertEntry(db,kind,accountId,amount,base,"Перевод",note,date.toString(),toId,credit)
        } else insertEntry(db,kind,accountId,amount,base,category,note,date.toString())
        if(tagIds.isNotEmpty()) assignTags(db,entryId,tagIds)
        entryId
    }
    /** Import parsed purchases only after explicit opt-in and RUB account selection.
     * No notification text, additional rewards, or financial identifiers are stored.
     * Entries are committed atomically; source keys block group/individual repost duplicates.
     */
    fun importBankPurchases(origin:String,purchases:List<BankPurchase>,happenedAt:Long):Int=mutation { db ->
        if (origin != BankPushParser.T_BANK_PACKAGE || purchases.isEmpty()) return@mutation 0
        val state=snapshot()
        if (!state.settings.bankImportEnabled) return@mutation 0
        val now=System.currentTimeMillis()
        // Refuse stale or implausibly future-dated pushes, rather than backfill an unknown history.
        if (happenedAt < now - 3L*24*60*60*1000 || happenedAt > now + 5L*60*1000) return@mutation 0
        val day=BankImportRules.notificationDay(happenedAt)
        val seconds=happenedAt/1000L
        var inserted=0
        purchases.distinct().take(15).forEach { purchase ->
            if (purchase.merchant.length !in 2..75 || purchase.amountMinor !in 1..Money.MAX_MINOR)
                return@forEach
            val account=BankSubaccounts.routeTBankPurchase(
                state.accounts,purchase,state.settings.bankImportAccountId) ?: return@forEach
            val prefix=BankImportRules.sourcePrefix(origin,account.id,day,purchase)
            val prior=mutableListOf<Long>()
            db.rawQuery("SELECT source_key FROM entries WHERE source_key LIKE ?",
                arrayOf("$prefix%") ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.removePrefix(prefix)?.toLongOrNull()?.let(prior::add)
                }
            }
            if (!BankImportRules.duplicate(prior,seconds)) {
                insertEntry(db,Kind.EXPENSE,account.id,purchase.amountMinor,purchase.amountMinor,
                    purchase.merchant,"",day,source="$prefix$seconds")
                inserted++
            }
        }
        inserted
    }

    fun editEntryDetails(id:Long,category:String,note:String,day:LocalDate,
        tagIds:List<Long>?=null)=mutation { db ->
        val e=snapshot().entries.first{it.id==id}
        require(e.kind in listOf(Kind.EXPENSE,Kind.INCOME) &&
            (e.sourceKey==null || e.sourceKey.startsWith("bank:v1:")))
        require(category.isNotBlank() && category.length<=80 && note.length<=500)
        db.update("entries",values("category" to category,"note" to note,"day" to day.toString()),"id=?",arrayOf(id.toString()))
        if(tagIds!=null) assignTags(db,id,tagIds)
    }
    fun deleteEntry(id:Long)=mutation { db ->
        val e=snapshot().entries.first {it.id==id}
        // Special transactions have corresponding lot or coupon state and cannot be removed independently.
        require(e.kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER) &&
            (e.sourceKey==null || e.sourceKey.startsWith("bank:v1:"))) { "Системную запись удалять нельзя" }
        db.delete("entries","id=?",arrayOf(id.toString()))
    }
    fun goal(title:String,target:Long,reserve:Boolean)=mutation { db ->
        require(title.isNotBlank() && target in 1..Money.MAX_MINOR)
        db.insertOrThrow("goals",null,values("title" to title,"target_minor" to target,"saved_minor" to 0L,"reserve" to reserve))
    }
    fun saved(goalId:Long,value:Long)=mutation { db ->
        require(value in 0..Money.MAX_MINOR)
        require(db.update("goals",values("saved_minor" to value),"id=?",arrayOf(goalId.toString()))==1)
    }
    fun plan(title:String,amount:Long,accountId:Long,due:LocalDate,frequency:String,auto:Boolean,reserve:Boolean)=mutation { db ->
        require(title.isNotBlank() && amount in 1..Money.MAX_MINOR && frequency in listOf("ONCE","MONTHLY","WEEKLY"))
        require(snapshot().accounts.any { it.id==accountId })
        db.insertOrThrow("plans",null,values("title" to title,"amount_minor" to amount,"account_id" to accountId,
            "due" to due.toString(),"frequency" to frequency,"auto" to auto,"reserve" to reserve,"anchor_day" to due.dayOfMonth))
    }
    fun postPlan(id:Long,today:LocalDate=LocalDate.now()):Boolean=mutation { db ->
        val current=snapshot()
        val plan=current.plans.first{it.id==id}
        val due=LocalDate.parse(plan.due)
        require(!due.isAfter(today))
        val account=current.accounts.first { it.id==plan.accountId }
        val key="plan:$id:$due"
        if(db.rawQuery("SELECT 1 FROM entries WHERE source_key=?",arrayOf(key)).use{it.moveToFirst()}) false
        else {
            insertEntry(db,Kind.EXPENSE,plan.accountId,plan.amountMinor,FxRates.toBase(current.settings,account.currency,plan.amountMinor),
                "Платежи",plan.title,due.toString(),source=key)
            if(plan.frequency=="ONCE") db.update("plans",values("auto" to false,"reserve" to false),"id=?",arrayOf(id.toString()))
            else db.update("plans",values("due" to LedgerMath.nextDate(due,plan.frequency,plan.anchorDay).toString()),"id=?",arrayOf(id.toString()))
            true
        }
    }
    fun processAutoPlans(today:LocalDate=LocalDate.now()) {
        // Invoked on app start; per-occurrence source_key prevents duplicate debits.
        var count=0
        while(count++<120) {
            val next=snapshot().plans.firstOrNull { it.auto && !LocalDate.parse(it.due).isAfter(today) } ?:break
            postPlan(next.id,today)
        }
    }
    fun budget(category:String,limitMinor:Long)=mutation { db ->
        require(category.isNotBlank() && category.length<=80 && limitMinor in 1..Money.MAX_MINOR)
        db.insertWithOnConflict("budgets",null,values("category" to category,"limit_minor" to limitMinor),SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun template(title:String,kind:String,category:String,accountId:Long,amount:Long)=mutation { db ->
        require(title.isNotBlank()&&kind in listOf(Kind.EXPENSE,Kind.INCOME)&&amount in 0..Money.MAX_MINOR)
        db.insertOrThrow("templates",null,values("title" to title,"kind" to kind,"category" to category,
            "account_id" to accountId,"amount_minor" to amount))
    }
    fun deleteTemplate(id:Long)=mutation { db ->
        require(db.delete("templates","id=?",arrayOf(id.toString()))==1) { "Шаблон не найден" }
    }
    fun bond(name:String,accountId:Long,nominal:Long,quote:Long,couponBps:Int):Long=mutation { db ->
        require(name.isNotBlank()&&nominal in 1..Money.MAX_MINOR&&quote in 0..Money.MAX_MINOR&&couponBps in 0..100000)
        require(snapshot().accounts.first{it.id==accountId}.currency=="RUB")
        db.insertOrThrow("bonds",null,values("name" to name,"nominal_minor" to nominal,"quote_minor" to quote,
            "coupon_bps" to couponBps,"account_id" to accountId))
    }
    fun updateQuote(id:Long,newQuote:Long)=mutation { db ->
        require(newQuote in 0..Money.MAX_MINOR)
        require(Math.multiplyExact(LedgerMath.bondQuantity(snapshot(),id),newQuote)<=Money.MAX_MINOR)
        require(db.update("bonds",values("quote_minor" to newQuote),"id=?",arrayOf(id.toString()))==1)
    }
    fun buyBond(bondId:Long,quantity:Long,priceEach:Long,fee:Long=0,date:LocalDate=LocalDate.now())=mutation { db ->
        require(quantity in 1..100000000 && priceEach in 1..Money.MAX_MINOR && fee in 0..Money.MAX_MINOR)
        val current=snapshot()
        val b=current.bonds.first{it.id==bondId}
        val newCount=Math.addExact(LedgerMath.bondQuantity(current,bondId),quantity)
        require(Math.multiplyExact(newCount,b.nominalMinor) <= Money.MAX_MINOR)
        require(Math.multiplyExact(newCount,b.quoteMinor) <= Money.MAX_MINOR)
        val amount=Math.addExact(Math.multiplyExact(quantity,priceEach),fee)
        require(amount in 1..Money.MAX_MINOR)
        insertEntry(db,Kind.BUY,b.accountId,amount,amount,"Инвестиции","Покупка ${b.name}: $quantity шт.",date.toString())
        db.insertOrThrow("lots",null,values("bond_id" to bondId,"quantity" to quantity,"cost_minor" to amount,"day" to date.toString()))
    }
    fun sellBond(bondId:Long,quantity:Long,priceEach:Long,fee:Long=0,date:LocalDate=LocalDate.now()):Long=mutation { db ->
        require(quantity in 1..100000000 && priceEach in 1..Money.MAX_MINOR && fee>=0)
        val s=snapshot();val bond=s.bonds.first{it.id==bondId};require(LedgerMath.bondQuantity(s,bondId)>=quantity)
        val proceeds=Math.subtractExact(Math.multiplyExact(quantity,priceEach),fee);require(proceeds in 1..Money.MAX_MINOR)
        var pending=quantity;var basis=0L
        s.lots.filter{it.bondId==bondId}.forEach { lot ->
            if(pending>0) {
                val sold=minOf(pending,lot.quantity)
                val consumed=Money.proportions(lot.costMinor,sold,lot.quantity)
                basis=Math.addExact(basis,consumed)
                if(sold==lot.quantity) db.delete("lots","id=?",arrayOf(lot.id.toString()))
                else db.update("lots",values("quantity" to lot.quantity-sold,"cost_minor" to lot.costMinor-consumed),
                    "id=?",arrayOf(lot.id.toString()))
                pending-=sold
            }
        }
        check(pending==0L)
        insertEntry(db,Kind.SELL,bond.accountId,proceeds,proceeds,"Инвестиции","Продажа ${bond.name}: $quantity шт.; FIFO база ${basis}",date.toString())
        proceeds-basis
    }
    fun coupon(bondId:Long,day:LocalDate,amount:Long):Long=mutation { db ->
        require(amount in 1..Money.MAX_MINOR);require(snapshot().bonds.any{it.id==bondId})
        db.insertOrThrow("coupons",null,values("bond_id" to bondId,"day" to day.toString(),
            "amount_minor" to amount,"posted" to false))
    }
    fun postCoupon(id:Long,today:LocalDate=LocalDate.now())=mutation { db ->
        val s=snapshot();val c=s.coupons.first{it.id==id};require(!c.posted&& !LocalDate.parse(c.day).isAfter(today))
        val b=s.bonds.first{it.id==c.bondId}
        insertEntry(db,Kind.COUPON,b.accountId,c.amountMinor,c.amountMinor,"Купоны",b.name,c.day,source="coupon:$id")
        db.update("coupons",values("posted" to true),"id=?",arrayOf(id.toString()))
    }
    fun setPreference(key:String,value:String)=mutation { db ->
        require(key in setOf("displayCurrency","usdPerBaseMicros","eurPerBaseMicros","bynPerBaseMicros",
            "extraReserveMinor","sharedReserveMinor","coveredPlansMinor","includeInvestments","seasonal","nextIncomeDate","themeMode","graphicsMode", "bankImportEnabled", "bankImportAccountId"))
        require(value.length<=50)
        if (key=="nextIncomeDate") LocalDate.parse(value)
        if (key=="bankImportEnabled") require(value == "0" || value == "1")
        if (key=="bankImportAccountId") {
            val id=value.toLongOrNull() ?: error("Некорректный идентификатор счёта")
            require(snapshot().accounts.any { it.id==id && it.currency=="RUB" }) {
                "Для автоматических покупок выберите рублёвый счёт"
            }
        }
        if (key=="themeMode") require(value in setOf("DARK","LIGHT","SYSTEM"))
        if (key=="graphicsMode") require(value in setOf("MAX","BALANCED","FAST"))
        if (key in setOf("usdPerBaseMicros","eurPerBaseMicros","bynPerBaseMicros")) {
            val normalized=value.toLongOrNull() ?: error("Некорректный курс")
            require(normalized in 10L..1_000_000_000_000L) { "Некорректный курс" }
        }
        db.insertWithOnConflict("preferences",null,values("key" to key,"value" to value),SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun validateImportedData(s:Snapshot) {
        require(s.accounts.isNotEmpty() && s.accounts.size<100000)
        require(s.accounts.all { a ->
            a.name.isNotBlank() && a.name.length<=80 && a.currency in listOf("RUB","USD","EUR","BYN") &&
                a.openingMinor in 0..Money.MAX_MINOR && a.openingBaseMinor in 0..Money.MAX_MINOR &&
                a.rateMicros in 1..100000000000L && (a.currency!="RUB"||a.rateMicros==1000000L) &&
                a.bankGroup.length<=60 && (a.cardLast4.isEmpty() || a.cardLast4.matches(Regex("[0-9]{4}"))) &&
                (a.cardLast4.isEmpty() || a.bankGroup.isNotBlank())
        })
        s.accounts.forEach { a ->
            BankSubaccounts.verifyMetadata(s.accounts.filter { it.id != a.id },a.bankGroup,a.cardLast4,a.id)
        }
        val accIds=s.accounts.map{it.id}.toSet()
        require(s.entries.all { e ->
            e.fromAccount in accIds && e.toAccount?.let{it in accIds}!=false &&
                e.kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER,Kind.BUY,Kind.SELL,Kind.COUPON) &&
                e.amountMinor in 1..Money.MAX_MINOR && e.baseMinor in 0..Money.MAX_MINOR &&
                e.category.length<=80 && e.note.length<=500 &&
                runCatching{LocalDate.parse(e.day)}.isSuccess &&
                (e.kind!=Kind.TRANSFER||(e.toAccount!=null&&e.toAmountMinor in 1..Money.MAX_MINOR))
        })
        require(s.plans.all {it.amountMinor in 1..Money.MAX_MINOR && it.accountId in accIds &&
            it.frequency in listOf("ONCE","MONTHLY","WEEKLY") && it.anchorDay in 1..31 &&
            runCatching{LocalDate.parse(it.due)}.isSuccess})
        require(s.goals.all {it.targetMinor in 1..Money.MAX_MINOR && it.savedMinor in 0..Money.MAX_MINOR})
        require(s.budgets.all{it.monthlyLimitMinor in 1..Money.MAX_MINOR})
        val bondIds=s.bonds.map{it.id}.toSet()
        require(s.bonds.all{it.accountId in accIds && it.nominalMinor in 1..Money.MAX_MINOR &&
            it.quoteMinor in 0..Money.MAX_MINOR && it.couponBps in 0..100000})
        require(s.lots.all{it.bondId in bondIds && it.quantity in 1..100000000 &&
            it.costMinor in 1..Money.MAX_MINOR && runCatching{LocalDate.parse(it.day)}.isSuccess})
        require(s.coupons.all{it.bondId in bondIds && it.amountMinor in 1..Money.MAX_MINOR &&
            runCatching{LocalDate.parse(it.day)}.isSuccess})
        require(s.templates.all{it.accountId in accIds&&it.kind in listOf(Kind.EXPENSE,Kind.INCOME)&&
            it.amountMinor in 0..Money.MAX_MINOR})
        require(s.categories.all { it.name.isNotBlank() && it.kind in listOf(Kind.EXPENSE,Kind.INCOME) &&
            runCatching { CategoryTagRules.color(it.colorHex) }.isSuccess &&
                runCatching { CategoryTagRules.icon(it.iconKey) }.isSuccess })
        require(s.tags.all { it.name.isNotBlank() && runCatching {CategoryTagRules.color(it.colorHex)}.isSuccess })
        require(s.entries.all { it.tags.size<=12 && it.tags.all { tag -> s.tags.any { def->def.name==tag } } })
        s.accounts.forEach{LedgerMath.accountBase(it,s.entries);LedgerMath.accountLocal(it,s.entries)}
        s.bonds.forEach{LedgerMath.bondMarketValue(s,it)}
    }
    private val backupTables=listOf("accounts","entries","goals","plans","budgets","templates","bonds","lots","coupons","preferences","categories","tags","entry_tags")
    fun exportJson():String {
        val result=JSONObject().put("format","karman-v1").put("schema",1)
        val db=readableDatabase
        backupTables.forEach { table ->
            val rows=JSONArray()
            db.query(table,null,null,null,null,null,null).use { c ->
                while(c.moveToNext()) {
                    val row=JSONObject()
                    for(i in 0 until c.columnCount) {
                        if(c.isNull(i)) row.put(c.getColumnName(i),JSONObject.NULL)
                        else when(c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER-> row.put(c.getColumnName(i),c.getLong(i))
                            Cursor.FIELD_TYPE_STRING->row.put(c.getColumnName(i),c.getString(i))
                            else ->error("Unexpected column type in $table")
                        }
                    }
                    rows.put(row)
                }
            }
            result.put(table,rows)
        }
        return result.toString(2)
    }
    fun importJson(json:String) {
        require(json.length<=30_000_000) { "Файл слишком велик" }
        val root=JSONObject(json);require(root.optString("format")=="karman-v1"&&root.optInt("schema")==1)
        val allowedColumns=mapOf(
            "accounts" to setOf("id","name","currency","opening_minor","opening_base_minor","rate_micros","included","bank_group","card_last4"),
            "entries" to setOf("id","day","kind","from_account","amount_minor","base_minor","category","note","to_account","to_amount_minor","source_key"),
            "goals" to setOf("id","title","target_minor","saved_minor","reserve"),
            "plans" to setOf("id","title","amount_minor","account_id","due","frequency","auto","reserve","anchor_day"),
            "budgets" to setOf("id","category","limit_minor"),
            "templates" to setOf("id","title","kind","category","account_id","amount_minor"),
            "bonds" to setOf("id","name","nominal_minor","quote_minor","coupon_bps","account_id"),
            "lots" to setOf("id","bond_id","quantity","cost_minor","day"),
            "coupons" to setOf("id","bond_id","day","amount_minor","posted"),
            "preferences" to setOf("key","value"),
            "categories" to setOf("id","name","kind","color_hex","icon_key"),
            "tags" to setOf("id","name","color_hex"),
            "entry_tags" to setOf("entry_id","tag_id")
        )
        val optionalTables=setOf("categories","tags","entry_tags")
        backupTables.forEach { name ->
            require((root.has(name) || name in optionalTables) &&
                (!root.has(name) || root.getJSONArray(name).length()<=100000))
        }
        mutation { db ->
            backupTables.asReversed().forEach { db.delete(it,null,null) }
            for(name in backupTables) {
                if(!root.has(name)) continue // Backward-compatible 1.0.6 backup
                val a=root.getJSONArray(name)
                for(i in 0 until a.length()) {
                    val o=a.getJSONObject(i); val cv=ContentValues()
                    val keys=o.keys()
                    while(keys.hasNext()) {
                        val key=keys.next();require(key in allowedColumns.getValue(name))
                        val v=o.get(key)
                        when(v) {
                            JSONObject.NULL -> cv.putNull(key)
                            is String -> {require(v.length<=2000);cv.put(key,v)}
                            is Number ->cv.put(key,v.toLong())
                            else ->error("Некорректное значение резервной копии")
                        }
                    }
                    db.insertOrThrow(name,null,cv)
                }
            }
            if(!root.has("categories")) seedCategoryCatalog(db)
            validateImportedData(snapshot())
        }
    }
}
