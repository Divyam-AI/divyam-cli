/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.selector

import ai.divyam.cli.selector.SelectorCommandUtils
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--start-timestamp",
                "2026-07-01",
                isEndBoundary = false,
            ),
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--end-timestamp",
                "2026-07-31",
                isEndBoundary = true,
            ),
        )

        val sourceSpecs = config.path("datasets").path("train_ds").path("source_specs")
        assertEquals("flow-1", sourceSpecs.path("flow_id").asText())
        assertEquals("2026-07-01T00:00:00", sourceSpecs.path("start_date").asText())
        assertEquals("2026-07-31T23:59:59", sourceSpecs.path("end_date").asText())
    }

    @Test
    fun `patch train dataset date range preserves full timestamps and offsets`() {
        val config = mapper.readTree(
            """{"datasets":{"train_ds":{"source_specs":{}}}}"""
        ) as ObjectNode

        SelectorCommandUtils.patchTrainDatasetDateRange(
            config,
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--start-timestamp",
                "2026-07-01T09:00:00+5.30",
                isEndBoundary = false,
            ),
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--end-timestamp",
                "2026-07-01T17:30:00+05:30",
                isEndBoundary = true,
            ),
        )

        val sourceSpecs = config.path("datasets").path("train_ds").path("source_specs")
        assertEquals("2026-07-01T09:00:00+05:30", sourceSpecs.path("start_date").asText())
        assertEquals("2026-07-01T17:30:00+05:30", sourceSpecs.path("end_date").asText())
    }

    @Test
    fun `timestamp range ordering compares instants across offsets`() {
        val config = mapper.readTree(
            """{"datasets":{"train_ds":{"source_specs":{}}}}"""
        ) as ObjectNode

        SelectorCommandUtils.patchTrainDatasetDateRange(
            config,
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--start-timestamp",
                "2026-07-01T09:00:00+05:30",
                isEndBoundary = false,
            ),
            SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--end-timestamp",
                "2026-07-01T04:00:00Z",
                isEndBoundary = true,
            ),
        )

        val sourceSpecs = config.path("datasets").path("train_ds").path("source_specs")
        assertEquals("2026-07-01T09:00:00+05:30", sourceSpecs.path("start_date").asText())
        assertEquals("2026-07-01T04:00:00Z", sourceSpecs.path("end_date").asText())
    }

    @Test
    fun `timestamp range rejects mixed offset and local values`() {
        val config = mapper.readTree(
            """{"datasets":{"train_ds":{"source_specs":{}}}}"""
        ) as ObjectNode

        val error = assertFailsWith<IllegalArgumentException> {
            SelectorCommandUtils.patchTrainDatasetDateRange(
                config,
                SelectorCommandUtils.TrainingWindowBoundary.parse(
                    "--start-timestamp",
                    "2026-07-01T09:00:00+05:30",
                    isEndBoundary = false,
                ),
                SelectorCommandUtils.TrainingWindowBoundary.parse(
                    "--end-timestamp",
                    "2026-07-01T17:30:00",
                    isEndBoundary = true,
                ),
            )
        }

        assertEquals(
            "--start-timestamp and --end-timestamp must both include UTC offsets when either value includes one",
            error.message,
        )
    }

    @Test
    fun `patch train dataset date range rejects an inverted range`() {
        val config = mapper.readTree("""{"datasets":{"train_ds":{}}}""") as ObjectNode

        val error = assertFailsWith<IllegalArgumentException> {
            SelectorCommandUtils.patchTrainDatasetDateRange(
                config,
                SelectorCommandUtils.TrainingWindowBoundary.parse(
                    "--start-timestamp",
                    "2026-08-01",
                    isEndBoundary = false,
                ),
                SelectorCommandUtils.TrainingWindowBoundary.parse(
                    "--end-timestamp",
                    "2026-07-31",
                    isEndBoundary = true,
                ),
            )
        }

        assertEquals("--start-timestamp must be on or before --end-timestamp", error.message)
    }
}
