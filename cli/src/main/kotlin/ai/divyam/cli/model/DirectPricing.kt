/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.model

import ai.divyam.data.model.TextPricing

internal data class DirectPricing(
    val textPricing: TextPricing,
    val currency: String,
    val perNTokens: Int,
)

internal fun resolveDirectPricing(
    inputPrice: Double?,
    outputPrice: Double?,
    currency: String?,
    perNTokens: Int?,
): DirectPricing? {
    if ((inputPrice == null) != (outputPrice == null)) {
        throw IllegalArgumentException("Use --input-price and --output-price together")
    }
    if (inputPrice == null) {
        if (currency != null || perNTokens != null) {
            throw IllegalArgumentException(
                "--currency and --per-n-tokens require --input-price and --output-price"
            )
        }
        return null
    }
    if (!inputPrice.isFinite() || inputPrice < 0 || !outputPrice!!.isFinite() || outputPrice < 0) {
        throw IllegalArgumentException("--input-price and --output-price must be finite non-negative values")
    }

    val resolvedCurrency = (currency ?: "USD").trim()
    if (resolvedCurrency.isEmpty()) {
        throw IllegalArgumentException("--currency must not be blank")
    }
    val resolvedPerNTokens = perNTokens ?: 1_000_000
    if (resolvedPerNTokens <= 0) {
        throw IllegalArgumentException("--per-n-tokens must be greater than 0")
    }

    return DirectPricing(
        textPricing = TextPricing(input = inputPrice, output = outputPrice),
        currency = resolvedCurrency,
        perNTokens = resolvedPerNTokens,
    )
}
