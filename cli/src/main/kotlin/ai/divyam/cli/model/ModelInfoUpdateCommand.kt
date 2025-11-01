package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.model.ModelPricingStore.Companion.pricingStore
import ai.divyam.data.model.ModelApiType
import ai.divyam.data.model.ModelProviderInfoUpdation
import ai.divyam.data.model.TextPricing
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(name = "update", description = ["Update model info"])
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
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Optional: service account id to associate the model " +
                "info with"],
    )
    private var serviceAccountId: String? = null

    // TODO: Does it make sense to allow model name update? Is it changing
    //  the model fundamentally?
    @Option(
        names = ["--model-name"],
        description = ["Optional: New name of the model is change is desired"],
    )
    private var modelName: String? = null

    @Option(
        names = ["--base-model-name"],
        description = ["Optional: New of the base model for this model"],
    )
    private var baseModelName: String? = null

    @Option(
        names = ["--unset-base-model-name"],
        description = ["Optional: Unset/resets the base model name to null"],
    )
    private var unsetBaseModelName: Boolean = false

    @Option(
        names = ["--provider-name"],
        description = ["Optional: Name of provider"],
    )
    private var providerName: String? = null

    @Option(
        names = ["--provider-api-key"],
        description = ["Optional: Provider API Key to make the calls while " +
                "proxying requests"],
        interactive = true,
        arity = "0..1"
    )
    private var providerApiKey: String? = null

    // TODO: DB calls this endpoint. Here it is base url...
    @Option(
        names = ["--provider-base-url"],
        description = ["Optional: Base URL to use for provider"],
    )
    private var providerBaseUrl: String? = null

    @Option(
        names = ["--api-type"],
        description = ["Optional: The API type to use for this mode." +
                $$"Valid values are ${COMPLETION-CANDIDATES}"]
    )
    private var apiType: ModelApiType? = null

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

    @Option(
        names = ["--is-selection-enabled"],
        arity = "1",
        description = ["Optional: indicates if selection is enabled for this model"],
    )
    var isSelectionEnabled: Boolean? = null

    @Option(
        names = ["--is-active"],
        arity = "1",
        description = ["Optional: indicates if this mode is active"],
    )
    var isSActive: Boolean? = null

    override fun execute(): Int {
        runBlocking {
            val modelPricing = if (!skipPricing) {
                val pricingStore = pricingStore(modelPricingYaml)
                val existingModelInfo = divyamClient.getModelInfoById(
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
                modelProviderInfoUpdation = ModelProviderInfoUpdation(
                    serviceAccountId = serviceAccountId,
                    providerName = providerName,
                    endpoint = providerBaseUrl,
                    apiType = apiType,
                    apiKeyModel = providerApiKey,
                    modelName = modelName,
                    baseModelName = baseModelName,
                    unsetBaseModelName = unsetBaseModelName,
                    textPricing = if (modelPricing != null) TextPricing(
                        input = modelPricing.textInputPrice,
                        output = modelPricing.textOutputPrice
                    ) else null,
                    currency = modelPricing?.currency,
                    perNTokens = modelPricing?.perNTokens,
                    configsModel = modelConfigsJson,
                    isSelectionEnabled = isSelectionEnabled,
                    isActive = isSActive
                )
            )

            printObjs(updated)
        }
        return 0
    }
}

