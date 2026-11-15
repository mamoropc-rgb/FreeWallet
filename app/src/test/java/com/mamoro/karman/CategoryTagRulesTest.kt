package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class CategoryTagRulesTest {
    @Test fun normalizeNamesAndRejectEmpty() {
        assertEquals("Кафе и рестораны",CategoryTagRules.name("  Кафе   и рестораны "))
        assertThrows(IllegalArgumentException::class.java) { CategoryTagRules.name("  ") }
    }
    @Test fun validatePaletteAndTags() {
        assertEquals("#48DBAB",CategoryTagRules.color("#48dbab"))
        assertEquals(listOf(1L,2L),CategoryTagRules.tagIds(listOf(1,1,2)))
        assertThrows(IllegalArgumentException::class.java) {CategoryTagRules.tagIds((1..13).map {it.toLong()})}
    }
    @Test fun rgbPaletteCanBeAnyHexColor() {
        assertEquals("#12ABEF", CategoryTagRules.color(" #12abef "))
        assertEquals("#000000", CategoryTagRules.color("#000000"))
        assertEquals("#FFFFFF", CategoryTagRules.color("#ffffff"))
        assertThrows(IllegalArgumentException::class.java) {CategoryTagRules.color("#1234")}
        assertThrows(IllegalArgumentException::class.java) {CategoryTagRules.color("#GG1122")}
        assertThrows(IllegalArgumentException::class.java) {CategoryTagRules.color("#11223344")}
    }
    @Test fun richIconsRetainOldKeys() {
        assertTrue(WalletIconCatalog.options.size>=250)
        assertEquals(WalletIconCatalog.options.size,WalletIconCatalog.options.map{it.key}.distinct().size)
        assertEquals("shopping",CategoryTagRules.icon("shopping"))
        assertEquals("Restaurant",CategoryTagRules.icon("Restaurant"))
        assertThrows(IllegalArgumentException::class.java) {CategoryTagRules.icon("random_key")}
    }
}
