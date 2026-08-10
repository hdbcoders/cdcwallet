package com.cdcwallet.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure decision behind the expanded hero's responsive layout: below the
 * threshold it keeps the two-column (left + right) mockup layout; at or
 * above it, the card stacks (top + bottom) so the category rows get full
 * width at large text sizes.
 */
class BalanceHeroLayoutTest {

    @Test
    fun belowThresholdKeepsSideBySideLayout() {
        assertEquals(false, shouldStackBalanceHero(1.0f))
        assertEquals(false, shouldStackBalanceHero(1.25f))
        assertEquals(false, shouldStackBalanceHero(1.49f))
    }

    @Test
    fun atOrAboveThresholdStacks() {
        assertEquals(true, shouldStackBalanceHero(1.5f))
        assertEquals(true, shouldStackBalanceHero(1.75f))
        assertEquals(true, shouldStackBalanceHero(2.0f))
    }

    @Test
    fun thresholdMatchesTheSpecifiedValue() {
        assertEquals(1.5f, BALANCE_STACK_THRESHOLD, 0f)
    }
}
