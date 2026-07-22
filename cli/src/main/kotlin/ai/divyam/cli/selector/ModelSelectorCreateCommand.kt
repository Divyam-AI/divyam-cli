/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.SelectorTrainingConfigurationInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File
import java.util.UUID

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
        names = ["--start-timestamp"],
        description = [
            "Optional: Start of the selector training-data window. Pair with --end-timestamp. " +
                "Accepted forms: YYYY-MM-DD (00:00:00), YYYY-MM-DDTHH:mm:ss, " +
                "YYYY-MM-DDTHH:mm:ssZ, or YYYY-MM-DDTHH:mm:ss[+/-]HH:MM " +
                "(IST example: 2026-07-01T09:00:00+05:30 = 2026-07-01T03:30:00Z). " +
                "If either boundary uses a UTC offset, both must. Use both flags with --extractor-strategy to create the training configuration, or to override the window in --config-file.",
        ],
    )
    private var startTimestamp: String? = null

    @Option(
        names = ["--end-timestamp"],
        description = [
            "Optional: End of the selector training-data window. Pair with --start-timestamp. " +
                "Accepted forms: YYYY-MM-DD (23:59:59), YYYY-MM-DDTHH:mm:ss, " +
                "YYYY-MM-DDTHH:mm:ssZ, or YYYY-MM-DDTHH:mm:ss[+/-]HH:MM " +
                "(IST example: 2026-07-01T17:30:00+05:30 = 2026-07-01T12:00:00Z). " +
                "If either boundary uses a UTC offset, both must. Use both flags with --extractor-strategy to create the training configuration, or to override the window in --config-file.",
        ],
    )
    private var endTimestamp: String? = null

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
                config = readConfigFile(configFile, resolvedServiceAccountId),
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

        if (configFile == null && (startTimestamp != null || endTimestamp != null)) {
            require(startTimestamp != null && endTimestamp != null) {
                "--start-timestamp and --end-timestamp must be provided together when no config file is supplied"
            }
        }
    }

    private fun readConfigFile(
        configFile: File?,
        serviceAccountId: String,
    ): SelectorTrainingConfigurationInput? {
        val file = configFile ?: return if (startTimestamp == null && endTimestamp == null) {
            null
        } else {
            createDateRangeConfig(serviceAccountId)
        }

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

        if (startTimestamp == null && endTimestamp == null) {
            return config
        }

        val jsonMapper = getJsonMapper()
        val configNode = jsonMapper.valueToTree<ObjectNode>(config)
        SelectorCommandUtils.patchTrainDatasetDateRange(
            configNode = configNode,
            startDate = startTimestamp?.let { parseBoundary("--start-timestamp", it, isEndBoundary = false) },
            endDate = endTimestamp?.let { parseBoundary("--end-timestamp", it, isEndBoundary = true) }
        )
        return jsonMapper.treeToValue(configNode, SelectorTrainingConfigurationInput::class.java)
    }

    private fun createDateRangeConfig(serviceAccountId: String): SelectorTrainingConfigurationInput =
        buildDateRangeConfig(
            jsonMapper = getJsonMapper(),
            serviceAccountId = serviceAccountId,
            extractorStrategy = requireNotNull(extractorStrategy),
            startDate = parseBoundary("--start-timestamp", requireNotNull(startTimestamp), isEndBoundary = false),
            endDate = parseBoundary("--end-timestamp", requireNotNull(endTimestamp), isEndBoundary = true),
        )

    private fun parseBoundary(
        optionName: String,
        value: String,
        isEndBoundary: Boolean,
    ): SelectorCommandUtils.TrainingWindowBoundary =
        SelectorCommandUtils.TrainingWindowBoundary.parse(optionName, value, isEndBoundary)

    companion object {
        internal fun buildDateRangeConfig(
            jsonMapper: ObjectMapper,
            serviceAccountId: String,
            extractorStrategy: String,
            startDate: SelectorCommandUtils.TrainingWindowBoundary,
            endDate: SelectorCommandUtils.TrainingWindowBoundary,
        ): SelectorTrainingConfigurationInput {
            val configNode = jsonMapper.createObjectNode()
            val trainDataset = configNode.putObject("datasets").putObject("train_ds")
            trainDataset.put(
                "name",
                "train_${serviceAccountId}_${UUID.randomUUID().toString().take(8)}",
            )
            trainDataset.put("min_rows", 1)
            trainDataset.put("source", "router_logs")
            trainDataset.put("reuse_existing", true)
            trainDataset.putObject("source_specs").put("ignore_control_bucket", true)
            configNode.putObject("stages")
                .putObject("selector_evaluation")
                .put("extractor_strategy", extractorStrategy)

            SelectorCommandUtils.patchTrainDatasetDateRange(
                configNode = configNode,
                startDate = startDate,
                endDate = endDate,
            )
            return jsonMapper.treeToValue(configNode, SelectorTrainingConfigurationInput::class.java)
        }
    }
}
