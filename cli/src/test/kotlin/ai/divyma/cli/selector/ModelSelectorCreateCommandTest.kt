/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.selector

import ai.divyam.cli.selector.ModelSelectorCreateCommand
import ai.divyam.cli.selector.SelectorCommandUtils
import ai.divyam.client.DivyamClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelSelectorCreateCommandTest {
    private val mapper = ObjectMapper().apply(DivyamClient.configureObjectMapper())

    @Test
    fun `date flags create a controller-compatible router log configuration without a config file`() {
        val config = ModelSelectorCreateCommand.buildDateRangeConfig(
            jsonMapper = mapper,
            serviceAccountId = "service-account-id",
            extractorStrategy = "default",
            startDate = SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--start-timestamp",
                "2026-07-01",
                isEndBoundary = false,
            ),
            endDate = SelectorCommandUtils.TrainingWindowBoundary.parse(
                "--end-timestamp",
                "2026-07-31",
                isEndBoundary = true,
            ),
        )

        val configNode = mapper.valueToTree<ObjectNode>(config)
        val trainDataset = configNode.path("datasets").path("train_ds")
        val sourceSpecs = trainDataset.path("source_specs")

        assertTrue(
            trainDataset.path("name").asText().startsWith("train_service-account-id_"),
        )
        assertEquals(1, trainDataset.path("min_rows").asInt())
        assertEquals("router_logs", trainDataset.path("source").asText())
        assertTrue(trainDataset.path("reuse_existing").asBoolean())
        assertEquals("2026-07-01T00:00:00", sourceSpecs.path("start_date").asText())
        assertEquals("2026-07-31T23:59:59", sourceSpecs.path("end_date").asText())
        assertTrue(sourceSpecs.path("ignore_control_bucket").asBoolean())
        assertEquals(
            "default",
            configNode.path("stages").path("selector_evaluation")
                .path("extractor_strategy").asText(),
        )
        assertFalse(configNode.path("stages").has("selector_training"))
        assertFalse(configNode.path("stages").has("enrichment"))
    }
}
