package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.model.ModelPricingStore.Companion.pricingStore
import ai.divyam.client.ModelProviderInfoUpdation
import ai.divyam.client.TextPricing
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(name = "update")
class ModelInfoUpdateCommand : BaseCommand() {
    // TODO: Consistent IDs types and scope of ids for all objects?
    @Option(
        names = ["--id"],
        description = ["Required: Id of model info to update"],
        required = true
    )
    private var id: Int = 0

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

    // TODO: Does it make sense to allow model name update? Is it changing
    //  the model fundamentally?
    @Option(
        names = ["--model-name"],
        description = ["Optional: New name of the model is change is desired"],
        required = true
    )
    private var modelName: String? = null

    // TODO: Does it make sense to allow model name update? Is it changing
    //  the model fundamentally?
    @Option(
        names = ["--provider-name"],
        description = ["Optional: Name of provider"],
    )
    private var providerName: String? = null

    @Option(
        names = ["--provider-api-key"],
        description = ["Optional: Provider API Key to make the calls while " +
                "proxying requests"],
    )
    private var providerApiKey: String? = null

    // TODO: DB calls this endpoint. Here it is base url...
    @Option(
        names = ["--provider-base-url"],
        description = ["Optional: Base URL to use for provider"],
    )
    private var providerBaseUrl: String? = null

    // TODO: Any better way of getting pricing done.
    @Option(
        names = ["--model-pricing-yaml"],
        description = ["Optional: Yaml file containing pricing for the " +
                "desired models. Uses inbuilt pricing card."],
    )
    private var modelPricingYaml: File? = null

    // TODO: Default  should be always skip?
    @Option(
        names = ["--skip-pricing-update"],
        description = ["Optional: Skip updating pricing information"],
    )
    private var skipPricing: Boolean = false

    // TODO: Apply to all models in list? Also name sounds strange.
    @Option(
        names = ["--model-configs-json"],
        description = ["Optional: JSON " +
                "string of model configs"]
    )
    private var modelConfigsJson: String? = null

    override fun execute(): Int {
        runBlocking {
            val modelPricing = if (!skipPricing) {
                val pricingStore = pricingStore(modelPricingYaml)
                val existingModelInfo = divyamClient.getModelInfo(
                    orgId = orgId,
                    modelInfoId = id
                )

                val queryProviderName = if (providerName != null) {
                    providerName!!
                } else {
                    existingModelInfo.nameProvider
                }

                val queryModelInfo = if (modelName != null) {
                    modelName!!
                } else {
                    existingModelInfo.nameModel
                }

                pricingStore.getModelPricing(
                    provider = queryProviderName,
                    model = queryModelInfo
                )
            } else {
                null
            }

            mutableListOf<Map<String, Any?>>()

            // Validate json.
            modelConfigsJson?.let {
                try {
                    ModelInfoCommand.parseJson(
                        it,
                        getJsonMapper()
                    )
                } catch (e: Exception) {
                    throw Exception("Invalid model-configs-json", e)
                }
            }

            val updated = divyamClient.updateModelInfo(
                orgId = orgId,
                modelInfoId = id,
                providerInfoUpdation = ModelProviderInfoUpdation(
                    serviceAccountId = serviceAccountId,
                    providerName = providerName,
                    endpoint = providerBaseUrl,
                    apiKeyModel = providerApiKey,
                    modelName = modelName,
                    textPricing = if (modelPricing != null) TextPricing(
                        input = modelPricing.textInputPrice,
                        output = modelPricing.textOutputPrice
                    ) else null,
                    currency = modelPricing?.currency,
                    perNTokens = modelPricing?.perNTokens,
                    configsModel = modelConfigsJson
                )
            )

            printObjs(updated)
        }
        return 0
    }
}

