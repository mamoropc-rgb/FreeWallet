package com.mamoro.karman

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression: upgrading an installed 1.0.4 database must never discard ledger records. */
@RunWith(RobolectricTestRunner::class)
class LedgerDbMigrationTest {
    @Test fun oldAccountAndEntrySurviveVersionTwoMigration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("karman_v1.db")
        val old=context.openOrCreateDatabase("karman_v1.db",Context.MODE_PRIVATE,null)
        old.execSQL("""CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,currency TEXT NOT NULL,opening_minor INTEGER NOT NULL,
            opening_base_minor INTEGER NOT NULL,rate_micros INTEGER NOT NULL,
            included INTEGER NOT NULL DEFAULT 1)""")
        old.execSQL("""CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,
            day TEXT NOT NULL,kind TEXT NOT NULL,from_account INTEGER NOT NULL,
            amount_minor INTEGER NOT NULL,base_minor INTEGER NOT NULL,
            category TEXT NOT NULL,note TEXT NOT NULL,to_account INTEGER,
            to_amount_minor INTEGER,source_key TEXT UNIQUE)""")
        old.execSQL("""INSERT INTO accounts(id,name,currency,opening_minor,opening_base_minor,rate_micros,included)
            VALUES(7,'Моя карта','RUB',100000,100000,1000000,1)""")
        old.execSQL("""INSERT INTO entries(id,day,kind,from_account,amount_minor,base_minor,category,note)
            VALUES(8,'2026-11-15','EXPENSE',7,61096,61096,'Пятёрочка','')""")
        old.version=1
        old.close()
        val helper=LedgerDb(context)
        try {
            val db=helper.writableDatabase
            assertEquals(3,db.version)
            db.rawQuery("PRAGMA table_info(accounts)",null).use { cursor ->
                val names=mutableSetOf<String>()
                while(cursor.moveToNext()) names.add(cursor.getString(1))
                assertTrue(names.contains("bank_group"))
                assertTrue(names.contains("card_last4"))
            }
            db.rawQuery("SELECT id,name,opening_minor,bank_group,card_last4 FROM accounts",null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7L,cursor.getLong(0))
                assertEquals("Моя карта",cursor.getString(1))
                assertEquals(100000L,cursor.getLong(2))
                assertEquals("",cursor.getString(3))
                assertEquals("",cursor.getString(4))
            }
            db.rawQuery("SELECT count(*) FROM categories",null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getLong(0)>0)
            }
            db.rawQuery("SELECT id,amount_minor FROM entries",null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(8L,cursor.getLong(0))
                assertEquals(61096L,cursor.getLong(1))
            }
        } finally {
            helper.close()
        }
    }
}
