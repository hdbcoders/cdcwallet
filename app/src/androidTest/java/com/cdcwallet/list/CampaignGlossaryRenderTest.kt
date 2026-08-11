package com.cdcwallet.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.ui.components.BalanceHero
import com.cdcwallet.ui.list.ListSummary
import com.cdcwallet.ui.theme.AppLanguage
import com.cdcwallet.ui.theme.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Display-time glossary localization end-to-end: with [LocalAppLanguage]
 * set, the hero's category rows render the translated name for glossary
 * entries (Supermarket) while unknown categories pass through raw. Ties
 * `LocalAppLanguage` → `localizeCategory` → visible text.
 */
@RunWith(AndroidJUnit4::class)
class CampaignGlossaryRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val summary = ListSummary(
        total = BigDecimal("1234.50"),
        categoryTotals = listOf(
            CategoryBalance("Supermarket", BigDecimal("200.00")),
            CategoryBalance("Heartland", BigDecimal("1000.00")),
            CategoryBalance("Climate", BigDecimal("34.50")),
        ),
        linkCount = 3,
    )

    @Test
    fun supermarketRendersTranslatedPerLanguage() {
        var language by mutableStateOf(AppLanguage.EN)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    BalanceHero(summary = summary, collapsed = false, onToggle = {})
                }
            }
        }
        val expectations = listOf(
            AppLanguage.EN to "Supermarket",
            AppLanguage.ZH to "超级市场",
            AppLanguage.MS to "Pasar Raya",
            AppLanguage.TA to "சூப்பர்மார்க்கெட்",
        )
        expectations.forEach { (lang, expectedText) ->
            language = lang
            composeRule.waitForIdle()
            composeRule.onNodeWithText(expectedText, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun unknownCategoriesRenderRawInEveryLanguage() {
        var language by mutableStateOf(AppLanguage.EN)
        // Use a summary with categories that have NO glossary entries -
        // they must render raw in every language.
        val unknownSummary = summary.copy(
            categoryTotals = listOf(
                CategoryBalance("Other", BigDecimal("10.00")),
                CategoryBalance("Dining", BigDecimal("20.00")),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    BalanceHero(summary = unknownSummary, collapsed = false, onToggle = {})
                }
            }
        }
        listOf(AppLanguage.EN, AppLanguage.ZH, AppLanguage.MS, AppLanguage.TA).forEach { lang ->
            language = lang
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Other", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText("Dining", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun localizedCategoryStillWorksWhenHeroCollapsed() {
        var collapsed by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ZH) {
                    // Start collapsed; the label + total show, no categories.
                    BalanceHero(summary = summary, collapsed = collapsed, onToggle = { collapsed = !collapsed })
                }
            }
        }
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        // Expand: the localized category row appears.
        composeRule.onNodeWithTag("balance-hero").performClick()
        composeRule.onNodeWithText("超级市场", useUnmergedTree = true).assertIsDisplayed()
    }
}
