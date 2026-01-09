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
        names = ["--state"],
        description = [$$"Required: Selector state. ${COMPLETION-CANDIDATES}"],
        required = true
    )
    private lateinit var state: ModelSelectorState

    @Option(
        names = ["-c", "--config-file"],
        description = ["Optional: Config file (YAML or JSON) to use for the selector"],
    )
    private var configFile: File? = null

    private fun readConfigFile(): SelectorTrainingConfigurationInput? {
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

    @Option(
        names = ["-x", "--extractor-strategy", "--extractor"],
        description = ["Optional: Extractor strategy to use for the selector"],
    )
    private var extractorStrategy: String? = null

    override fun execute(): Int {
        val trainingConfig: SelectorTrainingConfigurationInput? =
            readConfigFile()

        val newModelSelector = runBlocking {
            val modelSelectorCreateRequest = ModelSelectorCreateRequest(
                orgId = orgId,
                serviceAccountId = serviceAccountId,
                name = name,
                state = state,
                endpoint = selectorEndpoint,
                config = trainingConfig,
                extractorStrategy = extractorStrategy
            )
            divyamClient.createModelSelector(
                modelSelectorCreateRequest = modelSelectorCreateRequest
            )
        }
        printObjs(newModelSelector)
        return 0
    }
}