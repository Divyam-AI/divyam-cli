/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.format

import ai.divyam.cli.base.OutputFormat
import ai.divyam.cli.format.Printing
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PrintingTest {
    private data class DetailConfig(val apiKey: String, val label: String)
    private data class SelectorDetail(val config: DetailConfig)

    @Test
    fun `redacts nested detail credentials in every output format`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val selector = SelectorDetail(DetailConfig("test-secret", "visible"))

        try {
            System.setOut(PrintStream(output))

            OutputFormat.entries.forEach { format ->
                output.reset()
                Printing.printObjs(
                    selector,
                    format,
                    redactKeys = setOf("api_key")
                )

                val rendered = output.toString()
                assertFalse(rendered.contains("test-secret"), "format=$format")
                assertTrue(rendered.contains("[REDACTED]"), "format=$format")

                if (format == OutputFormat.JSON) {
                    assertEquals(
                        "[REDACTED]",
                        Printing.getJsonMapper().readTree(rendered).at("/config/apiKey").asText()
                    )
                }
            }
        } finally {
            System.setOut(originalOut)
        }
    }
}
