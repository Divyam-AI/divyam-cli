/**
 * Copyright 2025-2026 DivyamAI Technologies Private Limited
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.data.model.ModelSelectorCandidateModel
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Utility functions shared across selector commands.
 */
object SelectorCommandUtils {
    /**
     * Updates the name of a dataset entry in selector training configuration JSON.
     *
     * The dataset key must be one of the keys under `datasets`, for example:
     * - "train_ds"
     * - "eval_ds"
     * - "calibration_ds"
     *
     * @param configNode Selector training configuration JSON root node
     * @param datasetKey Key under `datasets` to update (e.g. train_ds)
     * @param datasetName New dataset name to set
     * @throws IllegalArgumentException if the datasets node or dataset key is missing
     */
    fun patchDatasetName(configNode: ObjectNode, datasetKey: String, datasetName: String) {
        val datasets = configNode.get("datasets") as? ObjectNode
            ?: throw IllegalArgumentException("config has no datasets")

        val datasetNode = datasets.get(datasetKey) as? ObjectNode
            ?: throw IllegalArgumentException("config has no datasets.$datasetKey")
        datasetNode.put("name", datasetName)
    }

    /**
     * Parses a comma-separated string of candidate models into a list of ModelSelectorCandidateModel.
     *
     * Each model can be specified as:
     * - "model_name" (provider will be null)
     * - "provider:model_name" (both provider and model specified)
     *
     * @param candidateModelString Comma-separated string of provider:model pairs
     * @return List of ModelSelectorCandidateModel or null if input is null
     * @throws IllegalArgumentException if the format is invalid
     */
    fun parseCandidateModels(candidateModelString: String?): List<ModelSelectorCandidateModel>? {
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
}
