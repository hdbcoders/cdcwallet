package com.hdbcoders.cdcwallet.addflow

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.FakeVoucherRepository
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.extraction.ExtractionResult
import com.hdbcoders.cdcwallet.extraction.VoucherExtractor
import com.hdbcoders.cdcwallet.ui.add.AddVoucherScreen
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit C9 (spec 00 §0.6 REQ-11, refactor L8): display-time glossary
 * localization applies to APP-GENERATED strings too - the add-success message
 * must render the LOCALIZED campaign name ("CDC Vouchers 2026 (June)" ->
 * "CDC 消费券 2026 (六月)" in Chinese), never the raw scraped name echoed
 * verbatim into user-facing text. `CampaignGlossaryRenderTest` pins the
 * hero/card renderers; this pins the flagship app-generated example.
 */
@RunWith(AndroidJUnit4::class)
class AddSuccessMessageLocalizationInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addSuccessMessageLocalizesCampaignTokensPerLanguage() {
        val repo = FakeVoucherRepository()
        val flow = AddVoucherFlow(repo, object : VoucherExtractor {
            override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
                ExtractionResult.Success(
                    campaignName = "CDC Vouchers 2026 (June)",
                    validityStatus = ValidityStatus.ACTIVE,
                    expiryDate = LocalDate.of(2026, 6, 30),
                    categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
                )
        })

        var language by mutableStateOf(AppLanguage.EN)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    AddVoucherScreen(
                        flow = flow,
                        initialUrl = "https://voucher.redeem.gov.sg/TokenJune",
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Added:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // English is the source language: the raw name is echoed unchanged.
        assertTrue(
            composeRule.onAllNodesWithText("Added: CDC Vouchers 2026 (June)")
                .fetchSemanticsNodes().isNotEmpty(),
        )

        // Chinese: the glossary replaces the campaign tokens at display time -
        // "Vouchers" -> 消费券, "June" -> 六月 - and the raw spellings are gone.
        language = AppLanguage.ZH
        composeRule.waitForIdle()
        assertTrue(
            "zh message must contain the localized Vouchers token",
            composeRule.onAllNodesWithText("消费券", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "zh message must contain the localized June token",
            composeRule.onAllNodesWithText("六月", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("Added: CDC Vouchers 2026 (June)").assertDoesNotExist()
    }
}