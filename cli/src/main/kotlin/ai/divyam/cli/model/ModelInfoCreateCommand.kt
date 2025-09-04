package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.model.ModelPricingStore.Companion.pricingStore
import ai.divyam.client.ModelProviderInfo
import ai.divyam.client.ModelProviderInfoCreation
import ai.divyam.client.TextPricing
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(name = "create")
class ModelInfoCreateCommand : BaseCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "info with"],
        required = true
    )
    private var orgId: Int = 0

    @Option(
        names = ["--sa-id", "--service-account-id"],
        description = ["Optional: service account id to associate the model " +
                "info with"],
    )
    private var serviceAccountId: String? = null

    @Option(
        names = ["--provider-name"],
        description = ["Required: Name of provider"],
        required = true
    )
    private lateinit var providerName: String

    @Option(
        names = ["--provider-api-key"],
        description = ["Required: Provider API Key to make the calls while proxying requests"],
        required = true
    )
    private lateinit var providerApiKey: String

    @Option(
        names = ["--provider-base-url"],
        description = ["Required: Base URL to use for provider"],
        required = true
    )
    private lateinit var providerBaseUrl: String

    @Option(
        names = ["--model-names"],
        description = ["Required: Comma-separated list of model names which are to be allowed for the sepcified service account id(sa_id)"],
        split = ",",
        required = true
    )
    private lateinit var modelNames: List<String>

    // TODO: Apply to all models in list? Also name sounds strange.
    @Option(
        names = ["--model-configs-json"],
        description = ["Optional JSON " +
                "string of model configs"]
    )
    private var modelConfigsJson: String = "{}"

    // TODO: Any better way of getting pricing done. Also allow to skip pricing?
    @Option(
        names = ["--model-pricing-yaml"],
        description = ["Optional: Yaml file containing pricing for the " +
                "desired models. Uses inbuilt pricing card."],
    )
    private var modelPricingYaml: File? = null

    override fun execute(): Int {
        // TODO: Is allowing same model info, provider info tuple to be
        //  readded. is this expected.
        val mInfos = runBlocking {
            val pricingStore = pricingStore(modelPricingYaml)

            val pricingErrorModelList = mutableListOf<Map<String, Any?>>()

            val mInfos = mutableListOf<ModelProviderInfo>()
            for (modelName in modelNames) {
                val modelPricing = try {
                    pricingStore.getModelPricing(
                        provider = providerName,
                        model = modelName
                    )
                } catch (e: Exception) {
                    pricingErrorModelList.add(
                        mapOf(
                            "provider" to providerName,
                            "model" to modelName,
                            "error" to e.message
                        )
                    )
                    continue
                }

                // Validate json.
                try {
                    ModelInfoCommand.parseJson(
                        modelConfigsJson,
                        getJsonMapper()
                    )
                } catch (e: Exception) {
                    throw Exception("Invalid model-configs-json", e)
                }

                val mInfo = divyamClient.createModelInfo(
                    orgId = orgId,
                    providerInfoCreation = ModelProviderInfoCreation(
                        serviceAccountId = serviceAccountId,
                        nameProvider = providerName,
                        endpoint = providerBaseUrl,
                        apiKeyModel = providerApiKey,
                        nameModel = modelName,
                        textPricing = TextPricing(
                            input = modelPricing.textInputPrice,
                            output = modelPricing.textOutputPrice
                        ),
                        currency = modelPricing.currency,
                        perNTokens = modelPricing.perNTokens,
                        configsModel = modelConfigsJson
                    )
                )

                mInfos.add(mInfo)
            }


            if (pricingErrorModelList.isNotEmpty()) {
                throw Exception(
                    "Failed to fetch model pricing for the following models " +
                            "${
                                getJsonMapper().writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(pricingErrorModelList)
                            }"
                )
            }

            mInfos
        }

        printObjs(mInfos)
        return 0
    }
}