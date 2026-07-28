/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.data.model.EvaluationParamsInput
import ai.divyam.data.model.ExperimentDatasetInfo
import ai.divyam.data.model.ExperimentDatasetsInput
import ai.divyam.data.model.SelectorTrainingConfigurationInput
import ai.divyam.data.model.SourceSpec
import ai.divyam.data.model.StageWiseParamsInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A flag value to write into the config, addressed by the field's JSON path. */
data class ConfigOverride(val value: Any?, val path: List<String>) {
    constructor(value: Any?, vararg path: String) : this(value, path.toList())
}

/**
 * Builds the complete selector config the CLI sends to the controller.
 *
 * Required (no-default) fields are computed here; every other default comes from the OpenAPI spec,
 * baked into the generated models. Overrides are applied last, so precedence is
 * flag > config file > spec default. Adding a new flag is one [ConfigOverride] entry at the call
 * site plus its option — no plumbing here.
 */
class SelectorConfigBuilder(private val mapper: ObjectMapper) {

    fun build(
        fileConfig: SelectorTrainingConfigurationInput?,
        serviceAccountId: String,
        extractorStrategy: String?,
        overrides: List<ConfigOverride>,
    ): SelectorTrainingConfigurationInput {
        val base = fileConfig ?: buildFromScratch(
            serviceAccountId,
            requireNotNull(extractorStrategy) {
                "An extractor strategy is required to build a selector config without a config file"
            },
        )
        val tree = mapper.valueToTree<ObjectNode>(base)
        overrides.filter { it.value != null }.forEach { tree.setAtPath(it.path, it.value!!) }
        return mapper.treeToValue(tree, SelectorTrainingConfigurationInput::class.java)
    }

    /** Mirrors divyamlibs' from_router_logs_with_lookback_days: a 30-day router-logs window, UTC. */
    private fun buildFromScratch(
        serviceAccountId: String,
        extractorStrategy: String,
    ): SelectorTrainingConfigurationInput {
        val end = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        val start = end.minusDays(LOOKBACK_DAYS)
        val shortUuid = UUID.randomUUID().toString().replace("-", "").take(8)
        val name = "${serviceAccountId}_$shortUuid"
        return SelectorTrainingConfigurationInput(
            datasets = ExperimentDatasetsInput(
                trainDs = ExperimentDatasetInfo(
                    name = name,
                    sourceSpecs = SourceSpec(
                        startDate = start.atStartOfDay().format(ISO_SECONDS),
                        endDate = end.atStartOfDay().format(ISO_SECONDS),
                    ),
                ),
            ),
            stages = StageWiseParamsInput(
                selectorEvaluation = EvaluationParamsInput(extractorStrategy = extractorStrategy),
            ),
        )
    }

    /** Sets [value] at [path], creating intermediate objects as needed. */
    private fun ObjectNode.setAtPath(path: List<String>, value: Any) {
        var node = this
        for (key in path.dropLast(1)) {
            node = node.get(key) as? ObjectNode ?: mapper.createObjectNode().also { node.replace(key, it) }
        }
        node.replace(path.last(), mapper.valueToTree(value))
    }

    private companion object {
        const val LOOKBACK_DAYS = 30L
        val ISO_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
