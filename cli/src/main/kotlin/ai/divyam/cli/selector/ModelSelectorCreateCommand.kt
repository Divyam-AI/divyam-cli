/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.SelectorTrainingConfigurationInput
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File
import java.time.LocalDate

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
        names = ["--start-date"],
        description = ["Optional: Inclusive start date for datasets.train_ds.source_specs (format: YYYY-MM-DD). Requires --config-file."],
    )
    private var startDate: String? = null

    @Option(
        names = ["--end-date"],
        description = ["Optional: Inclusive end date for datasets.train_ds.source_specs (format: YYYY-MM-DD). Requires --config-file."],
    )
    private var endDate: String? = null

    override fun execute(): Int {
        validateOptions()
        val newModelSelector = runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val resolvedServiceAccountId = getSaId(serviceAccountId)
            val modelSelectorCreateRequest = ModelSelectorCreateRequest(
                orgId = resolvedOrgId,
                serviceAccountId = resolvedServiceAccountId,
                name = name,
                endpoint = selectorEndpoint,
                config = readConfigFile(configFile),
                extractorStrategy = extractorStrategy,
                evalId = evalId,
                candidateModels = SelectorCommandUtils.parseCandidateModels(candidateModels)
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

        if (configFile == null && (startDate != null || endDate != null)) {
            throw IllegalArgumentException("--start-date and --end-date require --config-file")
        }
    }

    private fun readConfigFile(configFile: File?): SelectorTrainingConfigurationInput? {
        val file = configFile ?: return null

        if (!file.exists()) {
            throw IllegalArgumentException("Config file does not exist: ${file.absolutePath}")
        }

        val config = when (val extension = file.extension.lowercase()) {
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

        if (startDate == null && endDate == null) {
            return config
        }

        val jsonMapper = getJsonMapper()
        val configNode = jsonMapper.valueToTree<ObjectNode>(config)
        SelectorCommandUtils.patchTrainDatasetDateRange(
            configNode = configNode,
            startDate = startDate?.let { parseDate("--start-date", it) },
            endDate = endDate?.let { parseDate("--end-date", it) }
        )
        return jsonMapper.treeToValue(configNode, SelectorTrainingConfigurationInput::class.java)
    }

    private fun parseDate(optionName: String, value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (exception: Exception) {
        throw IllegalArgumentException("$optionName must use YYYY-MM-DD: $value", exception)
    }
}
