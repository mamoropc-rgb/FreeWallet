package com.mamoro.karman

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LedgerTaxonomyTest {
    private lateinit var db:LedgerDb
    @Before fun prepare() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("karman_v1.db")
        db=LedgerDb(context)
    }
    @After fun finish() = db.close()

    @Test fun createTagAssignToEntryAndRestoreBackup() {
        val account=db.snapshot().accounts.first().id
        val tag1=db.saveTag(null,"Работа","#48DBAB")
        val tag2=db.saveTag(null,"Поездка","#A773FF")
        val entry=db.entry(Kind.EXPENSE,account,61096,"Продукты",tagIds=listOf(tag1,tag2,tag1))
        assertEquals(setOf("Работа","Поездка"),db.snapshot().entries.first().tags.toSet())
        val exported=db.exportJson()
        db.saveTag(tag1,"Служба","#48DBAB")
        assertTrue("Служба" in db.snapshot().entries.first().tags)
        db.importJson(exported)
        assertEquals(setOf("Работа","Поездка"),db.snapshot().entries.first().tags.toSet())
        assertEquals(61096L,db.snapshot().entries.first().amountMinor)
        assertEquals(entry,db.snapshot().entries.first().id)
    }

    @Test fun renameCategoryPreservesHistoryAndBudget() {
        val account=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,account,2500,"Продукты")
        db.budget("Продукты",60000)
        val previous=db.snapshot().categories.first {it.kind==Kind.EXPENSE && it.name=="Продукты"}
        db.saveCategory(previous.id,"Супермаркеты",Kind.EXPENSE,"#FF858E","shopping")
        assertEquals("Супермаркеты",db.snapshot().entries.first().category)
        assertEquals("Супермаркеты",db.snapshot().budgets.first().category)
        assertEquals(-2500L,LedgerMath.cashBase(db.snapshot()))
        db.deleteCategory(previous.id)
        assertEquals("Супермаркеты",db.snapshot().entries.first().category)
    }

    @Test fun deletingTagDoesNotDeleteTransaction() {
        val account=db.snapshot().accounts.first().id
        val tag=db.saveTag(null,"Нужное","#A773FF")
        db.entry(Kind.INCOME,account,10000,"Зарплата",tagIds=listOf(tag))
        db.deleteTag(tag)
        assertTrue(db.snapshot().entries.first().tags.isEmpty())
        assertEquals(10000L,LedgerMath.cashBase(db.snapshot()))
    }

    @Test fun oldBackupWithoutCategoriesAndTagsCanBeRestored() {
        val account=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,account,1200,"Продукты",date=LocalDate.parse("2026-11-15"))
        val backup=JSONObject(db.exportJson())
        backup.remove("categories");backup.remove("tags");backup.remove("entry_tags")
        db.importJson(backup.toString())
        assertEquals(1,db.snapshot().entries.size)
        assertTrue(db.snapshot().categories.isNotEmpty())
        assertEquals(-1200L,LedgerMath.cashBase(db.snapshot()))
    }
}
