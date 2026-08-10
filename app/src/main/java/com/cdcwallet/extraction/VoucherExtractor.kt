package com.cdcwallet.extraction

import android.content.Context

/**
 * The add-time call site (spec 02 §2.4). Declared separately so Packages 3 and 4
 * can depend on the contract without the engine's internals, and so tests can
 * substitute a fake. ExtractionEngine is the one production implementation.
 */
fun interface VoucherExtractor {
    suspend fun extractForAdd(context: Context, url: String): ExtractionResult
}
