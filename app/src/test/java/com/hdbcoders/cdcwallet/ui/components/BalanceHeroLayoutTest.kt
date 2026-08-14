package com.hdbcoders.cdcwallet.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure decision behind the expanded hero's responsive layout: the card
 * keeps the two-column (left + right) mockup layout while every category
 * row fits in the category column; as soon as ANY row would intersect with
 * its own balance (needs more width than the column provides), the card
 * stacks (top + bottom) so the rows get full width.
 */
class BalanceHeroLayoutTest {

    @Test
    fun allRowsFittingColumnWidthKeepSideBySideLayout() {
        assertEquals(false, shouldStackBalanceHero(400f, listOf(100f, 200f, 300f)))
        assertEquals(false, shouldStackBalanceHero(400f, listOf(400f)))
        assertEquals(false, shouldStackBalanceHero(400f, emptyList()))
    }

    @Test
    fun anyRowWiderThanColumnWidthStacks() {
        assertEquals(true, shouldStackBalanceHero(400f, listOf(100f, 401f)))
        assertEquals(true, shouldStackBalanceHero(400f, listOf(401f, 100f)))
        assertEquals(true, shouldStackBalanceHero(400f, listOf(100f, 200f, 401f)))
    }

    @Test
    fun singleRowAtExactColumnWidthStaysSideBySide() {
        // Equality is a fit: the row shares the line exactly.
        assertEquals(false, shouldStackBalanceHero(400f, listOf(400f)))
    }

    @Test
    fun noCategoriesNeverStacks() {
        assertEquals(false, shouldStackBalanceHero(400f, emptyList()))
    }
}
