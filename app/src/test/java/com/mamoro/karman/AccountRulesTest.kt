package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class AccountRulesTest {
    @Test fun tableIdsNeverCollideInTheSameLazyColumn() {
        assertNotEquals(AccountRowKeys.account(1),AccountRowKeys.bond(1))
        assertEquals("account:1",AccountRowKeys.account(1))
        assertEquals("bond:1",AccountRowKeys.bond(1))
    }
    @Test fun linkedInvestmentPreventsAccountDeletion() {
        val account=Account(1,"Счёт","RUB",0,0,1000000)
        val extra=Account(2,"Счёт 2","RUB",0,0,1000000)
        val bond=Bond(1,"Облигация",10000,10000,500,1)
        val s=Snapshot(accounts=listOf(account,extra),bonds=listOf(bond))
        assertFalse(AccountRules.canDelete(s,1))
        assertTrue(AccountRules.canDelete(s,2))
    }
}
