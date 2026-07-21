/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.SelectorTrainingConfigurationInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@CommandLine.Command(name = "clone", description = ["Clone an existing selector"])
class ModelSelectorCloneCommand : BaseCommand() {
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
        names = ["--from-id"],
        description = ["Required: ID of the source model selector to clone"],
        required = true
    )
    private var fromSelectorId: Int = 0

    @Option(
        names = ["--name", "-n"],
        description = ["Optional: Name for the cloned selector. Defaults to '<source_name>_clone'"],
        required = false
    )
    private var name: String? = null

    @Option(
        names = ["--recreate-dataset"],
        description = ["Optional: Recreate the dataset with new date range. " +
                "If provided, updates train_ds source_specs with start and end dates."],
        required = false
    )
    private var recreateDataset: Boolean = false

    @Option(
        names = ["--start-date"],
        description = ["Optional: Start date for dataset recreation (format: YYYY-MM-DD). " +
                "Defaults to yesterday - 30 days. Only used with --recreate-dataset."],
        required = false
    )
    private var startDate: String? = null

    @Option(
        names = ["--end-date"],
        description = ["Optional: End date for dataset recreation (format: YYYY-MM-DD). " +
                "Defaults to yesterday. Only used with --recreate-dataset."],
        required = false
    )
    private var endDate: String? = null

    @Option(
        names = ["--candidate-models", "--candidates", "-m"],
        description = ["Optional: Candidate models to use for the cloned selector as a comma separated list of provider:model pairs"],
        required = false
    )
    private var candidateModels: String? = null

    @Option(
        names = ["--dataset-name"],
        description = ["Optional: Sets datasets.train_ds.name. Applied after --recreate-dataset if both are set. " +
                "Use --train-dataset-name, --eval-dataset-name, or --calibration-dataset-name to target a specific dataset."],
        required = false
    )
    private var datasetName: String? = null

    @Option(
        names = ["--train-dataset-name"],
        description = ["Optional: Sets datasets.train_ds.name explicitly."],
        required = false
    )
    private var trainDatasetName: String? = null

    @Option(
        names = ["--eval-dataset-name"],
        description = ["Optional: Sets datasets.eval_ds.name."],
        required = false
    )
    private var evalDatasetName: String? = null

    @Option(
        names = ["--calibration-dataset-name"],
        description = ["Optional: Sets datasets.calibration_ds.name."],
        required = false
    )
    private var calibrationDatasetName: String? = null

    private fun requiresSourceConfigOverrides(): Boolean =
        datasetName != null ||
            trainDatasetName != null ||
            evalDatasetName != null ||
            calibrationDatasetName != null

    /**
     * Updates the config JSON with a new train_ds section for dataset recreation.
     *
     * @param configJson The original config JSON string
     * @param jsonMapper The Jackson ObjectMapper to use for JSON manipulation
     * @param serviceAccountId The service account ID to include in the dataset name
     * @param startDate Optional start date string (YYYY-MM-DD), defaults to yesterday - 30 days
     * @param endDate Optional end date string (YYYY-MM-DD), defaults to yesterday
     * @return Updated config JSON string with new train_ds section
     */
    private fun recreateTrainDatasetConfig(
        configJson: String,
        jsonMapper: ObjectMapper,
        serviceAccountId: String?,
        startDate: String?,
        endDate: String?
    ): String {
        val configNode = jsonMapper.readTree(configJson) as ObjectNode

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val yesterday = LocalDate.now().minusDays(1)

        val resolvedEndDate: LocalDateTime = endDate?.let {
            LocalDate.parse(it).atStartOfDay().plusDays(1).minusSeconds(1)
        } ?: yesterday.atStartOfDay().plusDays(1).minusSeconds(1)

        val resolvedStartDate: LocalDateTime = startDate?.let {
            LocalDate.parse(it).atStartOfDay()
        } ?: yesterday.minusDays(30).atStartOfDay()

        // Generate unique dataset name using serviceAccountId, dates, and random hash
        val saIdPart = serviceAccountId ?: "no_sa"
        val randomHash = UUID.randomUUID().toString().take(8)
        val generatedTrainDatasetName =
            "train_${saIdPart}_${resolvedStartDate.toLocalDate().format(dateOnlyFormatter)}_${resolvedEndDate.toLocalDate().format(dateOnlyFormatter)}_$randomHash"

        // Create or get datasets node
        val datasetsNode = (configNode.get("datasets") as? ObjectNode)
            ?: jsonMapper.createObjectNode().also { configNode.set<ObjectNode>("datasets", it) }

        // Create new train_ds object
        val trainDsNode = jsonMapper.createObjectNode()
        trainDsNode.put("name", generatedTrainDatasetName)
        trainDsNode.put("source", "router_logs")
        trainDsNode.put("reuse_existing", true)

        // Create source_specs with dates
        val sourceSpecsNode = jsonMapper.createObjectNode()
        sourceSpecsNode.put("start_date", resolvedStartDate.format(dateFormatter))
        sourceSpecsNode.put("end_date", resolvedEndDate.format(dateFormatter))
        trainDsNode.set<ObjectNode>("source_specs", sourceSpecsNode)

        // Set train_ds in datasets
        datasetsNode.set<ObjectNode>("train_ds", trainDsNode)
        return jsonMapper.writeValueAsString(configNode)
    }

    /**
     * Applies optional clone-time overrides onto the selector training config JSON.
     *
     * Only dataset override flags that were explicitly passed (non-null properties) are applied;
     * if none are set, the original JSON is returned unchanged.
     *
     * @param configJson The original config JSON string
     * @param jsonMapper The Jackson ObjectMapper to use for JSON manipulation
     * @return Updated config JSON string with all requested overrides applied
     */
    private fun applyConfigOverrides(configJson: String, jsonMapper: ObjectMapper): String {
        if (!requiresSourceConfigOverrides()) {
            return configJson
        }

        // Parse JSON into a mutable tree so we can surgically update nested fields.
        val configNode = jsonMapper.readTree(configJson) as ObjectNode

        // If requested, update dataset names in the `datasets` section.
        datasetName?.let {
            SelectorCommandUtils.patchDatasetName(configNode, "train_ds", it)
        }
        trainDatasetName?.let {
            SelectorCommandUtils.patchDatasetName(configNode, "train_ds", it)
        }
        evalDatasetName?.let {
            SelectorCommandUtils.patchDatasetName(configNode, "eval_ds", it)
        }
        calibrationDatasetName?.let {
            SelectorCommandUtils.patchDatasetName(configNode, "calibration_ds", it)
        }

        // Serialize the modified JSON tree back into a string for type conversion (Output -> Input).
        return jsonMapper.writeValueAsString(configNode)
    }

    override fun execute(): Int {
        val clonedSelector = runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val resolvedServiceAccountId = getSaId(serviceAccountId)
            // First, find the source selector by listing all selectors
            val allSelectors = divyamClient.listModelSelectors(
                orgId = resolvedOrgId,
                serviceAccountId = resolvedServiceAccountId,
                modelSelectorState = null
            )

            val sourceSelector = allSelectors.find { it.id == fromSelectorId }
                ?: throw IllegalArgumentException(
                    "Model selector with ID $fromSelectorId not found. " +
                            "Please verify the selector ID exists and you have access to it."
                )

            // Validate config exists when recreate-dataset is requested
            if (recreateDataset && sourceSelector.config == null) {
                throw IllegalArgumentException(
                    "Cannot recreate dataset: Source selector (ID: $fromSelectorId) has no configuration. " +
                            "The --recreate-dataset flag requires the source selector to have an existing config."
                )
            }

            if (requiresSourceConfigOverrides() && sourceSelector.config == null) {
                throw IllegalArgumentException(
                    "Source selector (ID: $fromSelectorId) has no configuration. " +
                            "Config overrides (dataset name flags) " +
                            "require the source selector to have an existing config."
                )
            }

            // Convert config from Output to Input format using JSON serialization
            // Note: Output and Input types have different JSON property names that need mapping
            val configInput: SelectorTrainingConfigurationInput? = sourceSelector.config?.let { configOutput ->
                val jsonMapper = getJsonMapper()
                var configJson = jsonMapper.writeValueAsString(configOutput)

                // If recreating dataset, update config with new train_ds section
                if (recreateDataset) {
                    configJson = recreateTrainDatasetConfig(
                        configJson = configJson,
                        jsonMapper = jsonMapper,
                        serviceAccountId = sourceSelector.serviceAccountId,
                        startDate = startDate,
                        endDate = endDate
                    )
                }

                configJson = applyConfigOverrides(configJson, jsonMapper)

                jsonMapper.readValue(configJson, SelectorTrainingConfigurationInput::class.java)
            }

            // Create the cloned selector with the same configuration
            val clonedName = name ?: "${sourceSelector.name}_clone"
            val createRequest = ModelSelectorCreateRequest(
                orgId = sourceSelector.orgId ?: getOrgId(orgId),
                serviceAccountId = requireNotNull(sourceSelector.serviceAccountId) {
                    "Source selector has no service account id"
                },
                name = clonedName,
                config = configInput,
                endpoint = sourceSelector.endpoint,
                candidateModels = SelectorCommandUtils.parseCandidateModels(candidateModels)
            )
            divyamClient.createModelSelector(
                modelSelectorCreateRequest = createRequest
            )
        }

        printObjs(clonedSelector)
        return 0
    }
}
