/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.selector

import ai.divyam.cli.selector.SelectorCommandUtils
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectorCommandUtilsTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `patch train dataset date range uses inclusive day boundaries`() {
        val config = mapper.readTree(
            """{"datasets":{"train_ds":{"source_specs":{"flow_id":"flow-1"}}}}"""
        ) as ObjectNode

        SelectorCommandUtils.patchTrainDatasetDateRange(
            config,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-31")
        )

        val sourceSpecs = config.path("datasets").path("train_ds").path("source_specs")
        assertEquals("flow-1", sourceSpecs.path("flow_id").asText())
        assertEquals("2026-07-01T00:00:00", sourceSpecs.path("start_date").asText())
        assertEquals("2026-07-31T23:59:59", sourceSpecs.path("end_date").asText())
    }

    @Test
    fun `patch train dataset date range rejects an inverted range`() {
        val config = mapper.readTree("""{"datasets":{"train_ds":{}}}""") as ObjectNode

        val error = assertFailsWith<IllegalArgumentException> {
            SelectorCommandUtils.patchTrainDatasetDateRange(
                config,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-07-31")
            )
        }

        assertEquals("--start-date must be on or before --end-date", error.message)
    }
}
