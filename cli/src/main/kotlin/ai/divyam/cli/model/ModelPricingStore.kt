package ai.divyam.cli.model

import ai.divyam.client.reflection.Reflectable
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.LocalDate
import java.math.BigDecimal

@Reflectable
data class ModelPricing(
    val provider: String,
    val model: String,
    val currency: String,

    @param:JsonProperty("text_input_price")
    val textInputPrice: BigDecimal,

    @param:JsonProperty("text_output_price")
    val textOutputPrice: BigDecimal,

    @param:JsonProperty("per_n_tokens")
    val perNTokens: Int,

    @param:JsonProperty("effective_start_date")
    @param:JsonFormat(pattern = "yyyy-MM-dd")
    val effectiveStartDate: LocalDate,

    @param:JsonProperty("effective_end_date")
    @param:JsonFormat(pattern = "yyyy-MM-dd")
    val effectiveEndDate: LocalDate? = null
)

class ModelPricingStore(inputStream: InputStream) {
    companion object {
        internal fun pricingStore(modelPricingYaml: File?): ModelPricingStore {
            val inputStream = if (modelPricingYaml != null) {
                FileInputStream(modelPricingYaml)
            } else {
                object {}.javaClass.getResourceAsStream(
                    "/model-pricing" +
                            ".yaml"
                ) ?: throw Exception(
                    "Cannot find model " +
                            "pricing resource"
                )
            }

            val pricingStore = inputStream.use {
                ModelPricingStore(inputStream)
            }
            return pricingStore
        }
    }

    private val modelPricingData: Map<String, List<ModelPricing>>

    init {
        modelPricingData = loadModelPricingData(inputStream)
    }

    private fun loadModelPricingData(inputStream: InputStream): Map<String, List<ModelPricing>> {
        val mapper = YAMLMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())

        val rawData: Map<String, Map<String, List<Map<String, Any>>>> =
            mapper.readValue(inputStream)

        val pricingMap = mutableMapOf<String, MutableList<ModelPricing>>()

        for ((provider, models) in rawData) {
            for ((modelName, pricingList) in models) {
                val key = "$provider/$modelName"
                val providerModelList =
                    pricingMap.getOrPut(key) { mutableListOf() }

                for (details in pricingList) {
                    // Merge provider + model into details before deserialization
                    val enriched = mapOf(
                        "provider" to provider,
                        "model" to modelName
                    ) + details
                    val pricing =
                        mapper.convertValue(enriched, ModelPricing::class.java)
                    providerModelList.add(pricing)
                }
            }
        }
        return pricingMap
    }

    fun getModelPricing(
        provider: String,
        model: String,
        forDate: LocalDate? = null
    ): ModelPricing {
        val date = forDate ?: LocalDate.now()
        val key = "$provider/$model"
        val providerModelList = modelPricingData[key].orEmpty()

        val matchingEntries = providerModelList.filter { entry ->
            entry.provider == provider &&
                    entry.model == model &&
                    !entry.effectiveStartDate.isAfter(date) &&
                    (entry.effectiveEndDate == null || !entry.effectiveEndDate.isBefore(
                        date
                    ))
        }

        if (matchingEntries.isEmpty()) {
            throw IllegalStateException(
                "Valid model pricing entry not found for provider: $provider, model: $model, effective_date: $date"
            )
        }

        return matchingEntries.maxByOrNull { it.effectiveStartDate }
            ?: throw IllegalStateException("Unexpected error computing max effective_start_date")
    }
}
