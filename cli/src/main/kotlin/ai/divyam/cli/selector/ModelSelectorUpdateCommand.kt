/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.ModelSelectorUpdateRequest
import ai.divyam.data.model.ModelSelectorCandidateModel
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update", description = ["Update a selector"])
class ModelSelectorUpdateCommand : BaseCommand() {
    // TODO: Service account id not updatable?. Model info allows update.
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
        names = ["--eval-id"],
        description = ["Optional: Eval id to use for the selector"],
    )
    private var evalId: Int? = null

    @Option(
        names = ["--candidate-models", "--candidates","-m"],
        description = ["Optional: Candidate models to use for the selector as a comma separated list of provider:model pairs"],
    )
    private var candidateModels: String? = null

    @Option(
        names = ["--state"],
        description = [$$"Optional: New model selector state.  ${COMPLETION-CANDIDATES}"]
    )
    private var state: ModelSelectorState? = null

    private fun parseCandidateModels(candidateModelString: String?): List<ModelSelectorCandidateModel>? {
    return candidateModelString?.split(",")
        ?.map { it.trim() }
        ?.also { tokens ->
            require(tokens.none { it.isEmpty() }) {
                "Candidate models list contains empty entries"
            }
        }
        ?.map { token ->
            val parts = token.split(":", limit = 2)

            when (parts.size) {
                1 -> {
                    require(parts[0].isNotBlank()) {
                        "Model name cannot be blank"
                    }
                    ModelSelectorCandidateModel(
                        model = parts[0],
                        provider = null
                    )
                }
                2 -> {
                    val provider = parts[0]
                    val model = parts[1]

                    require(provider.isNotBlank()) {
                        "Provider cannot be empty in '$token'"
                    }
                    require(model.isNotBlank()) {
                        "Model cannot be empty in '$token'"
                    }

                    ModelSelectorCandidateModel(
                        model = model,
                        provider = provider
                    )
                }
                else -> error("Invalid candidate model format: '$token'")
            }
        }
    }

    override fun execute(): Int {
        val updatedSelector = runBlocking {
            divyamClient.updateModelSelector(
                modelSelectorId = id,
                modelSelectorUpdateRequest = ModelSelectorUpdateRequest(
                    name = name,
                    state = state,
                    endpoint = selectorEndpoint,
                    evalId = evalId,
                    candidateModels = parseCandidateModels(candidateModels)
                ),
            )
        }
        printObjs(updatedSelector)
        return 0
    }
}