/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.SelectorTrainingConfigurationInput
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(name = "create", description = ["Create a selector"])
class ModelSelectorCreateCommand : BaseCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "selector with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Required: service account id to associate the model selector with. If omitted, falls back to the DIVYAM_SA_ID environment variable, then the current config file."],
    )
    private var serviceAccountId: String? = null

    @Option(
        names = ["--name"],
        description = ["Required: New model selector name"],
        required = true
    )
    private lateinit var name: String

    @Option(
        names = ["--selector-endpoint"],
        description = ["Optional: New model selector endpoint if change " +
                "desired"],
    )
    private var selectorEndpoint: String? = null

    @Option(
        names = ["-c", "--config-file"],
        description = ["Optional: Config file (YAML or JSON) to use for the selector"],
    )
    private var configFile: File? = null

    @Option(
        names = ["-x", "--extractor-strategy", "--extractor"],
        description = ["Optional: Extractor strategy to use for the selector"],
    )
    private var extractorStrategy: String? = null

    @Option(
        names = ["--eval-id"],
        description = ["Optional: Eval id to use for the selector"],
    )
    private var evalId: Int? = null

    @Option(
        names = ["--candidate-models", "--candidates", "-m"],
        description = ["Optional: Candidate models to use for the selector as a comma separated list of provider:model pairs"],
    )
    private var candidateModels: String? = null

    @Option(
        names = ["--min-dataset-rows"],
        description = ["Optional: Minimum number of rows the training source dataset must have before training starts"],
    )
    private var minDatasetRows: Int? = null

    @Option(
        names = ["--start-timestamp"],
        description = [
            "Optional: Inclusive start of the training-data window. " +
                "A date (YYYY-MM-DD) starts at 00:00:00, or pass a full ISO-8601 timestamp. " +
                "e.g. 2026-07-01  or  2026-07-01T09:00:00+05:30. " +
                "Pair with --end-timestamp when no config file is given.",
        ],
    )
    private var startTimestamp: String? = null

    @Option(
        names = ["--end-timestamp"],
        description = [
            "Optional: Inclusive end of the training-data window. " +
                "A date (YYYY-MM-DD) ends at 23:59:59, or pass a full ISO-8601 timestamp. " +
                "e.g. 2026-07-14  or  2026-07-14T17:30:00+05:30. " +
                "Pair with --start-timestamp when no config file is given.",
        ],
    )
    private var endTimestamp: String? = null

    override fun execute(): Int {
        validateOptions()
        val newModelSelector = runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val resolvedServiceAccountId = getSaId(serviceAccountId)
            val config = SelectorConfigBuilder(getJsonMapper()).build(
                fileConfig = readConfigFile(configFile),
                serviceAccountId = resolvedServiceAccountId,
                extractorStrategy = extractorStrategy,
                overrides = listOf(
                    ConfigOverride(extractorStrategy, "stages", "selector_evaluation", "extractor_strategy"),
                    ConfigOverride(minDatasetRows, "datasets", "train_ds", "min_rows"),
                    ConfigOverride(startTimestamp, "datasets", "train_ds", "source_specs", "start_date"),
                    ConfigOverride(endTimestamp, "datasets", "train_ds", "source_specs", "end_date"),
                ),
            )
            val modelSelectorCreateRequest = ModelSelectorCreateRequest(
                orgId = resolvedOrgId,
                serviceAccountId = resolvedServiceAccountId,
                name = name,
                endpoint = selectorEndpoint,
                config = config,
                evalId = evalId,
                candidateModels = SelectorCommandUtils.parseCandidateModels(candidateModels),
            )
            divyamClient.createModelSelector(
                modelSelectorCreateRequest = modelSelectorCreateRequest
            )
        }
        printObjs(newModelSelector, skipKeys = setOf("config"))
        return 0
    }

    private fun validateOptions() {
        if (configFile == null && extractorStrategy == null) {
            throw IllegalArgumentException(
                "Either a config file (-c/--config-file) or an extractor strategy (-x/--extractor-strategy) must be provided"
            )
        }
        if (configFile == null && (startTimestamp == null) != (endTimestamp == null)) {
            throw IllegalArgumentException(
                "--start-timestamp and --end-timestamp must be provided together when no config file is supplied"
            )
        }
    }

    private fun readConfigFile(configFile: File?): SelectorTrainingConfigurationInput? {
        val file = configFile ?: return null

        if (!file.exists()) {
            throw IllegalArgumentException("Config file does not exist: ${file.absolutePath}")
        }

        return when (val extension = file.extension.lowercase()) {
            "yaml", "yml" -> getYamlMapper().readValue<SelectorTrainingConfigurationInput>(
                file
            )

            "json" -> getJsonMapper().readValue<SelectorTrainingConfigurationInput>(
                file
            )

            else -> throw IllegalArgumentException(
                "Unsupported config file format: $extension. Use .yaml, .yml, or .json"
            )
        }
    }
}