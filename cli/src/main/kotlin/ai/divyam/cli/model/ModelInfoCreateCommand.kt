/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.model.ModelPricingStore.Companion.pricingStore
import ai.divyam.data.model.Modality
import ai.divyam.data.model.ModelApiType
import ai.divyam.data.model.ModelProviderInfo
import ai.divyam.data.model.ModelProviderInfoCreation
import ai.divyam.data.model.TextPricing
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(name = "create", description = ["Create model info"])
class ModelInfoCreateCommand : BaseCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "info with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["service account id to associate the model " +
                "info with. If omitted, falls back to the DIVYAM_SA_ID environment variable, then the current config file."],
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
        interactive = true,
        arity = "0..1"
    )
    private var providerApiKey: String? = null

    @Option(
        names = ["--provider-base-url"],
        description = ["Required: Base URL to use for provider"],
        required = true
    )
    private lateinit var providerBaseUrl: String

    @Option(
        names = ["--api-type"],
        description = ["Optional: The API type to use for this mode." +
                $$"Valid values are ${COMPLETION-CANDIDATES}"]
    )
    private var apiType: ModelApiType? = null

    @Option(
        names = ["--model-names"],
        description = ["Required: Comma-separated list of model names which " +
                "are to be allowed for the specified service account id" +
                "(sa_id). Use model_name:base_model_name to specify a base " +
                "model."],
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

    @Option(
        names = ["--supported-modalities"],
        description = ["Optional: Comma separated list of supported modalities. " +
                $$"Valid values are ${COMPLETION-CANDIDATES}"],
        split = ","
    )
    var supportedModalities: List<Modality> = listOf(Modality.text)

    @Option(
        names = ["--is-selection-enabled"],
        arity = "1",
        description = ["Optional: indicates if selection is enabled for these models. Defaults to true."],
    )
    var isSelectionEnabled: Boolean? = null

    override fun execute(): Int {
        // TODO: Is allowing same model info, provider info tuple to be
        //  re-added. is this expected?
        if (providerApiKey == null) {
            throw Exception("Provider API Key is required")
        }

        val mInfos = runBlocking {
            val pricingStore = pricingStore(modelPricingYaml)

            val pricingErrorModelList = mutableListOf<Map<String, Any?>>()

            val mInfos = mutableListOf<ModelProviderInfo>()
            for (modelNameInput in modelNames) {
                val parts = modelNameInput.trim().split(":")
                val modelName = parts.first().trim()
                val baseModelName = if (parts.size > 1) {
                    parts[1].trim()
                } else {
                    null
                }

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

                val resolvedOrgId = getOrgId(orgId)
                val resolvedServiceAccountId = getSaId(serviceAccountId)
                val mInfo = divyamClient.createModelInfo(
                    orgId = resolvedOrgId,
                    modelProviderInfoCreation = ModelProviderInfoCreation(
                        serviceAccountId = resolvedServiceAccountId,
                        nameProvider = providerName,
                        endpoint = providerBaseUrl,
                        apiType = apiType,
                        apiKeyModel = providerApiKey!!,
                        nameModel = modelName,
                        baseModelName = baseModelName,
                        textPricing = TextPricing(
                            input = modelPricing.textInputPrice,
                            output = modelPricing.textOutputPrice
                        ),
                        currency = modelPricing.currency,
                        perNTokens = modelPricing.perNTokens,
                        configsModel = modelConfigsJson,
                        supportedModalities = supportedModalities,
                        isSelectionEnabled = isSelectionEnabled
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
