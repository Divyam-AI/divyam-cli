/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.LambdaConfig
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.ModelSelectorUpdateRequest
import ai.divyam.data.model.WTPConfig
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update", description = ["Update a selector"])
class ModelSelectorUpdateCommand : BaseCommand() {
    @Option(
        names = ["--id"],
        description = ["The model selector id to update"],
        required = true
    )
    private var id: Int = 0

    @Option(
        names = ["--name"],
        description = ["Optional: New model selector name if change desired"],
    )
    private var name: String? = null

    @Option(
        names = ["--selector-endpoint"],
        description = ["Optional: New model selector endpoint if change " +
                "desired"],
    )
    private var selectorEndpoint: String? = null

    @Option(
        names = ["--retire"],
        description = ["Set selector state to INACTIVE"],
    )
    private var retire: Boolean = false

    @Option(
        names = ["--to-prod"],
        description = ["Set selector state to PROD"],
    )
    private var toProd: Boolean = false

    @Option(
        names = ["--lambda"],
        description = ["Lambda value to use for both high quality and cost efficient lambda in wtp_config"],
    )
    private var lambda: Double? = null

    @Option(
        names = ["--high-quality-lambda"],
        description = ["High quality lambda value for wtp_config. Must be used together with --cost-savings-lambda and without --lambda"],
    )
    private var highQualityLambda: Double? = null

    @Option(
        names = ["--cost-savings-lambda"],
        description = ["Cost savings (efficient) lambda value for wtp_config. Must be used together with --high-quality-lambda and without --lambda"],
    )
    private var costSavingsLambda: Double? = null

    private fun validateAndBuildState(): ModelSelectorState? {
        require(!(retire && toProd)) {
            "Cannot specify both --retire and --to-prod flags"
        }
        return when {
            retire -> ModelSelectorState.INACTIVE
            toProd -> ModelSelectorState.PROD
            else -> null
        }
    }

    private fun validateAndBuildWtpConfig(): WTPConfig? {
        val hasLambda = lambda != null
        val hasHighQuality = highQualityLambda != null
        val hasCostSavings = costSavingsLambda != null

        // No lambda params provided
        if (!hasLambda && !hasHighQuality && !hasCostSavings) {
            return null
        }

        // Validate mutual exclusivity
        if (hasLambda && (hasHighQuality || hasCostSavings)) {
            throw IllegalArgumentException(
                "--lambda cannot be used together with --high-quality-lambda or --cost-savings-lambda. " +
                "Use either --lambda alone, or both --high-quality-lambda and --cost-savings-lambda together."
            )
        }

        // If using separate lambdas, both must be provided
        if ((hasHighQuality || hasCostSavings) && !(hasHighQuality && hasCostSavings)) {
            throw IllegalArgumentException(
                "Both --high-quality-lambda and --cost-savings-lambda must be provided together."
            )
        }

        val lambdaConfig = if (hasLambda) {
            LambdaConfig(
                highQualityLambda = lambda!!,
                costEfficientLambda = lambda!!
            )
        } else {
            LambdaConfig(
                highQualityLambda = highQualityLambda!!,
                costEfficientLambda = costSavingsLambda!!
            )
        }

        return WTPConfig(lambdaCfg = lambdaConfig)
    }

    override fun execute(): Int {
        val state = validateAndBuildState()
        val wtpConfig = validateAndBuildWtpConfig()

        val updatedSelector = runBlocking {
            divyamClient.updateModelSelector(
                modelSelectorId = id,
                modelSelectorUpdateRequest = ModelSelectorUpdateRequest(
                    name = name,
                    state = state,
                    endpoint = selectorEndpoint,
                    wtpConfig = wtpConfig
                ),
            )
        }
        printObjs(updatedSelector, skipKeys = setOf("config"))
        return 0
    }
}