package com.mamoro.karman

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LedgerDbTest {
    private lateinit var db:LedgerDb
    @Before fun setup() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("karman_v1.db")
        db=LedgerDb(ctx)
    }
    @After fun teardown() {db.close()}
    @Test fun entriesPersistAndSourceValuesAreImmutable() {
        val a=db.snapshot().accounts.first()
        db.entry(Kind.INCOME,a.id,100000,"Зарплата")
        assertEquals(100000L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun duplicateCouponCannotBePostedTwice() {
        val id=db.snapshot().accounts.first().id
        val bond=db.bond("Облигация",id,100000,96000,1000)
        val coupon=db.coupon(bond,LocalDate.parse("2026-01-01"),2000)
        db.postCoupon(coupon,LocalDate.parse("2026-01-01"))
        assertEquals(2000L,LedgerMath.cashBase(db.snapshot()))
        assertThrows(IllegalArgumentException::class.java) {db.postCoupon(coupon,LocalDate.parse("2026-01-01"))}
    }
    @Test fun automaticScheduledPaymentPostsExactlyOnce() {
        val acc=db.snapshot().accounts.first().id
        db.plan("Услуга",500,acc,LocalDate.parse("2026-10-05"),"MONTHLY",true,true)
        db.processAutoPlans(LocalDate.parse("2026-10-05"))
        db.processAutoPlans(LocalDate.parse("2026-10-05"))
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun premiumThemeGraphicsAndIncomeDateAreSavedAndBackedUp() {
        db.setPreference("themeMode", "LIGHT")
        db.setPreference("graphicsMode", "MAX")
        db.setPreference("nextIncomeDate", "2026-12-03")
        val first = db.snapshot().settings
        assertEquals("LIGHT", first.themeMode)
        assertEquals("MAX", first.graphicsMode)
        assertEquals("2026-12-03", first.nextIncomeDate)
        val json = db.exportJson()
        db.setPreference("graphicsMode", "FAST")
        db.importJson(json)
        val restored = db.snapshot().settings
        assertEquals("MAX", restored.graphicsMode)
        assertEquals("LIGHT", restored.themeMode)
        assertEquals("2026-12-03", restored.nextIncomeDate)
        assertThrows(IllegalArgumentException::class.java) { db.setPreference("themeMode", "WRONG") }
        assertThrows(IllegalArgumentException::class.java) { db.setPreference("graphicsMode", "SUPERFAST") }
    }
    @Test fun backupExportAndImportRoundTrip() {
        val a=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,a,500,"Продукты")
        val json=db.exportJson()
        db.importJson(json)
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun deletingTemplateDoesNotDeletePastTransactions() {
        val account=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,account,2500,"Кафе")
        val templateId=db.template("Кофе",Kind.EXPENSE,"Кафе",account,2500)
        assertEquals(1,db.snapshot().templates.size)
        db.deleteTemplate(templateId)
        assertTrue(db.snapshot().templates.isEmpty())
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-2500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun creatingBondDoesNotCorruptAccountAndReopeningDataWorks() {
        val account=db.snapshot().accounts.first().id
        val bondId=db.bond("Без покупок",account,100_000L,95_000L,1000)
        val state=db.snapshot()
        assertEquals(account,bondId) // independent tables may share the same numeric ID
        assertNotEquals(AccountRowKeys.account(account),AccountRowKeys.bond(bondId))
        assertEquals(0L,LedgerMath.bondQuantity(state,bondId))
        db.close()
        db=LedgerDb(ApplicationProvider.getApplicationContext())
        assertEquals(1,db.snapshot().bonds.size)
        assertEquals(1,db.snapshot().accounts.size)
    }
    @Test fun accountEditAndSafeDelete() {
        val rub=db.snapshot().accounts.first().id
        val second=db.account("Резерв", "USD", 100_00L)
        db.editAccount(second,"Резервный", "EUR", 200_00L)
        assertEquals("EUR",db.snapshot().accounts.first { it.id==second }.currency)
        db.entry(Kind.INCOME,second,100_00L,"Подарок")
        db.editAccount(second,"Переименован", "EUR", 200_00L)
        assertEquals("Переименован",db.snapshot().accounts.first { it.id==second }.name)
        assertThrows(IllegalArgumentException::class.java) { db.editAccount(second,"Нельзя", "USD", 200_00L) }
        assertThrows(IllegalArgumentException::class.java) { db.deleteAccount(second) }
        db.deleteAccount(rub) // unused non-last account is deletable
        assertEquals(1,db.snapshot().accounts.size)
    }
    @Test fun removeEmptyAccountWithoutDeletingTransactions() {
        val rub=db.snapshot().accounts.first().id
        val disposable=db.account("Временный", "RUB", 0L)
        db.deleteAccount(disposable)
        assertEquals(listOf(rub),db.snapshot().accounts.map{it.id})
        assertThrows(IllegalArgumentException::class.java) {db.deleteAccount(rub)}
    }
    @Test fun liveFxFromSettingsDoesNotRewriteHistoricalTransactions() {
        val id=db.account("Карта", "USD",0L)
        db.setPreference("usdPerBaseMicros","10000") // 1 USD = 100 RUB
        db.entry(Kind.INCOME,id,100_00L,"Доход")
        assertEquals(1_000_000L,LedgerMath.cashBase(db.snapshot()))
        db.setPreference("usdPerBaseMicros","20000") // 1 USD = 50 RUB
        assertEquals(1_000_000L,LedgerMath.cashBase(db.snapshot()))
        db.entry(Kind.INCOME,id,100_00L,"Доход")
        assertEquals(1_500_000L,LedgerMath.cashBase(db.snapshot()))
        assertEquals(200_00L,LedgerMath.accountLocal(db.snapshot().accounts.first{it.id==id},db.snapshot().entries))
    }
    @Test fun bankImportIsOptInAndNeverAddsAdditionalCredits() {
        val account=db.snapshot().accounts.first().id
        val items=BankPushParser.parse(listOf(
            "Пятёрочка Покупка на 610,96 ₽, кешбэк 6 ₽, счёт карты *2017",
            "Магнит Покупка на 500 ₽, кешбэк 5 ₽, счёт карты *2017"))
        val time=System.currentTimeMillis()
        assertEquals(0,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,items,time))
        db.setPreference("bankImportAccountId",account.toString())
        db.setPreference("bankImportEnabled","1")
        assertEquals(0,db.importBankPurchases("com.other.bank",items,time))
        assertEquals(2,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,items,time))
        assertEquals(0,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,items,time+60_000))
        val entries=db.snapshot().entries
        assertEquals(2,entries.size)
        assertEquals(-111096L,LedgerMath.cashBase(db.snapshot()))
        assertTrue(entries.all { it.kind==Kind.EXPENSE && it.note.isEmpty() })
        assertEquals(setOf("Магнит","Пятёрочка"),entries.map { it.category }.toSet())
        assertTrue(db.exportJson().contains("bankImportEnabled"))
        assertFalse(db.exportJson().contains("кешбэк"))
    }
    @Test fun importedExpensesCanBeCorrectedInHistory() {
        val id=db.snapshot().accounts.first().id
        db.setPreference("bankImportAccountId",id.toString())
        db.setPreference("bankImportEnabled","1")
        val now=System.currentTimeMillis()
        val item=BankPurchase("Магнит",10000L)
        assertEquals(1,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,listOf(item),now))
        val entry=db.snapshot().entries.single()
        db.editEntryDetails(entry.id,"Продукты","Магнит",LocalDate.parse(entry.day))
        assertEquals("Продукты", db.snapshot().entries.single().category)
        db.deleteEntry(entry.id)
        assertTrue(db.snapshot().entries.isEmpty())
    }
    @Test fun bankSubaccountsKeepIndependentMoneyAndBackupLinks() {
        val a=db.account("Основная", "RUB", 500_00L,"Т-Банк","2017")
        val b=db.account("Сбережения", "RUB", 2000_00L,"Тинькофф","8002")
        assertEquals("Т-Банк",db.snapshot().accounts.first { it.id==b }.bankGroup)
        db.entry(Kind.EXPENSE,a,100_00L,"Продукты")
        db.entry(Kind.TRANSFER,a,250_00L,"Перевод",toId=b)
        val s=db.snapshot()
        assertEquals(150_00L,LedgerMath.accountLocal(s.accounts.first{it.id==a},s.entries))
        assertEquals(2250_00L,LedgerMath.accountLocal(s.accounts.first{it.id==b},s.entries))
        assertEquals(2400_00L,s.accounts.filter{it.bankGroup=="Т-Банк"}.sumOf{LedgerMath.accountBase(it,s.entries)})
        val json=db.exportJson()
        db.importJson(json)
        assertEquals("2017",db.snapshot().accounts.first{it.id==a}.cardLast4)
        assertEquals("8002",db.snapshot().accounts.first{it.id==b}.cardLast4)
        assertEquals(2400_00L,db.snapshot().accounts.filter{it.bankGroup=="Т-Банк"}.sumOf{LedgerMath.accountBase(it,db.snapshot().entries)})
        assertThrows(IllegalArgumentException::class.java) {
            db.editAccount(b,"Копия","RUB",2000_00L,"Т-Банк","2017")
        }
    }
    @Test fun bankPushRoutesToCorrectSubaccountAndSkipsUnknownCard() {
        val a=db.account("Карта", "RUB", 1000_00L,"Т-Банк","2017")
        val b=db.account("Вторая", "RUB", 2000_00L,"Т-Банк","8002")
        db.setPreference("bankImportAccountId",a.toString())
        db.setPreference("bankImportEnabled","1")
        val now=System.currentTimeMillis()
        val items=listOf(BankPurchase("Пятёрочка",61096,"2017"),BankPurchase("Магнит",50000,"8002"),
            BankPurchase("Фастфуд",10000,null),BankPurchase("Неизвестная карта",10000,"9999"))
        assertEquals(2,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,items,now))
        assertEquals(0,db.importBankPurchases(BankPushParser.T_BANK_PACKAGE,items,now+60_000))
        val s=db.snapshot()
        assertEquals(38904L,LedgerMath.accountLocal(s.accounts.first { it.id==a },s.entries))
        assertEquals(150000L,LedgerMath.accountLocal(s.accounts.first { it.id==b },s.entries))
        assertEquals(2,s.entries.size)
        assertTrue(s.entries.all{it.kind==Kind.EXPENSE})
    }
    @Test fun bondSaleUsesFifoAndCreditsWallet() {
        val a=db.snapshot().accounts.first().id
        val b=db.bond("Бонд",a,100000,100000,800)
        db.buyBond(b,1,95000)
        db.buyBond(b,5,75000)
        assertEquals(-470000L,LedgerMath.cashBase(db.snapshot()))
        val gain=db.sellBond(b,1,110000)
        assertEquals(15000L,gain)
        assertEquals(375000L,LedgerMath.bondInvested(db.snapshot(),b))
    }
}
