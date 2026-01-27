package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.ModelSelectorState
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
                "selector with"],
        required = true
    )
    private var orgId: Int = 0

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Optional: service account id to associate the model selector with"],
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
        val datasetName = "train_${saIdPart}_${resolvedStartDate.toLocalDate().format(dateOnlyFormatter)}_${resolvedEndDate.toLocalDate().format(dateOnlyFormatter)}_$randomHash"

        // Create or get datasets node
        val datasetsNode = (configNode.get("datasets") as? ObjectNode)
            ?: jsonMapper.createObjectNode().also { configNode.set<ObjectNode>("datasets", it) }

        // Create new train_ds object
        val trainDsNode = jsonMapper.createObjectNode()
        trainDsNode.put("name", datasetName)
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

    override fun execute(): Int {
        val clonedSelector = runBlocking {
            // First, find the source selector by listing all selectors
            val allSelectors = divyamClient.listModelSelectors(
                orgId = orgId,
                serviceAccountId = serviceAccountId,
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

                jsonMapper.readValue(configJson, SelectorTrainingConfigurationInput::class.java)
            }

            // Create the cloned selector with the same configuration
            val clonedName = name ?: "${sourceSelector.name}_clone"
            val createRequest = ModelSelectorCreateRequest(
                orgId = sourceSelector.orgId,
                serviceAccountId = sourceSelector.serviceAccountId,
                name = clonedName,
                config = configInput,
                endpoint = sourceSelector.endpoint
            )
            divyamClient.createModelSelector(
                modelSelectorCreateRequest = createRequest
            )
        }

        printObjs(clonedSelector)
        return 0
    }
}
