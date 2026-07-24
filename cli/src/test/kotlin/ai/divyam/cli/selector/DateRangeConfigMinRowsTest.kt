/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse

// Guards the single-source-of-truth contract for selector config defaults.
// The CLI must not ship its own min_rows default, so divyamlibs stays the owner.
// The client mapper omits absent fields, so an unset min_rows must not reach the wire.
class DateRangeConfigMinRowsTest {

    @Test
    fun `date range config omits min_rows so divyamlibs owns the default`() {
        val clientMapper = jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

        val config = ModelSelectorCreateCommand.buildDateRangeConfig(
            jsonMapper = clientMapper,
            serviceAccountId = "sa_test",
            extractorStrategy = "message_history",
            startDate = SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--start-timestamp", "2026-07-01", false
            ),
            endDate = SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--end-timestamp", "2026-07-14", true
            ),
        )

        val wire = clientMapper.writeValueAsString(config)
        val trainDs = clientMapper.readTree(wire).path("datasets").path("train_ds")

        assertFalse(
            trainDs.has("min_rows"),
            "The CLI must omit min_rows so the divyamlibs default applies, got: $wire"
        )
    }
}
