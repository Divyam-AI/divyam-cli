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

    private data class NestedRecord(val id: String, val createdAt: Int)
    private data class Wrapper(val key: NestedRecord, val apiKey: String)

    @Test
    fun `flattenKeys expands a nested field into columns for text output only`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val wrapper = Wrapper(NestedRecord("rec-1", 1700000000), "the-key")

        try {
            System.setOut(PrintStream(output))

            Printing.printObjs(wrapper, OutputFormat.TEXT, flattenKeys = setOf("key"))
            val text = output.toString()
            // A column per nested field, not the record's toString().
            assertTrue(text.contains("Id"), text)
            assertTrue(text.contains("rec-1"), text)
            assertFalse(text.contains("NestedRecord("), text)
            // Nested timestamps are formatted like top-level ones.
            assertFalse(text.contains("1700000000"), text)

            output.reset()
            Printing.printObjs(wrapper, OutputFormat.JSON, flattenKeys = setOf("key"))
            // Structured output keeps the shape the API returned.
            assertEquals(
                "rec-1",
                Printing.getJsonMapper().readTree(output.toString()).at("/key/id").asText()
            )
        } finally {
            System.setOut(originalOut)
        }
    }

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
