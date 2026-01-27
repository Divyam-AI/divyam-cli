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

    override fun execute(): Int {
        val newModelSelector = runBlocking {
            val modelSelectorCreateRequest = ModelSelectorCreateRequest(
                orgId = orgId,
                serviceAccountId = serviceAccountId,
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