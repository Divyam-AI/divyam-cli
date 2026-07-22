/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.model

import ai.divyam.cli.model.resolveDirectPricing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DirectPricingTest {
    @Test
    fun `returns null when direct pricing flags are absent`() {
        assertNull(resolveDirectPricing(null, null, null, null))
    }

    @Test
    fun `defaults currency and token unit`() {
        val pricing = resolveDirectPricing(0.1, 0.2, null, null)

        assertEquals(0.1, pricing?.textPricing?.input)
        assertEquals(0.2, pricing?.textPricing?.output)
        assertEquals("USD", pricing?.currency)
        assertEquals(1_000_000, pricing?.perNTokens)
    }

    @Test
    fun `requires both direct price flags`() {
        assertFailsWith<IllegalArgumentException> {
            resolveDirectPricing(0.1, null, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveDirectPricing(null, 0.2, null, null)
        }
    }

    @Test
    fun `rejects invalid direct pricing values`() {
        assertFailsWith<IllegalArgumentException> {
            resolveDirectPricing(Double.NaN, 0.2, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveDirectPricing(0.1, 0.2, "USD", 0)
        }
    }
}
