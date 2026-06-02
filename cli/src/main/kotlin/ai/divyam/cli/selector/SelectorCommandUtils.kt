/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.data.model.ModelSelectorCandidateModel
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Utility functions shared across selector commands.
 */
object SelectorCommandUtils {
    /**
     * Parses a comma-separated string of candidate model ids into a list of integers.
     *
     * Each id must be an integer token, for example:
     * - "1"
     * - "1,2,3"
     *
     * @param candidateModelIdsString Comma-separated string of candidate model ids
     * @return List of candidate model ids or null if input is null
     * @throws IllegalArgumentException if the format is invalid
     */
    fun parseCandidateModelIds(candidateModelIdsString: String?): List<Int>? {
        return candidateModelIdsString?.split(",")
            ?.map { it.trim() }
            ?.also { tokens ->
                require(tokens.none { it.isEmpty() }) {
                    "Candidate model ids list contains empty entries"
                }
            }
            ?.map { token ->
                token.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid candidate model id: '$token'")
            }
    }

    /**
     * Updates selector training and evaluation config with the given candidate model ids.
     *
     * Sets `stages.selector_training.candidate_model_ids` and, when present,
     * `stages.selector_evaluation.candidate_model_ids` to the same list.
     *
     * @param configNode Selector training configuration JSON root node
     * @param jsonMapper The Jackson ObjectMapper to use for JSON manipulation
     * @param candidateModelIds Candidate model ids to write into the config
     * @throws IllegalArgumentException if required config nodes are missing
     */
    fun patchCandidateModelIds(configNode: ObjectNode, jsonMapper: ObjectMapper, candidateModelIds: List<Int>) {
        val candidateModelIdsNode = candidateModelIdsToArrayNode(jsonMapper, candidateModelIds)

        val stages = configNode.get("stages") as? ObjectNode
            ?: throw IllegalArgumentException("config has no stages")

        val selectorTraining = stages.get("selector_training") as? ObjectNode
            ?: throw IllegalArgumentException("config has no stages.selector_training")
        selectorTraining.set<JsonNode>("candidate_model_ids", candidateModelIdsNode)

        (stages.get("selector_evaluation") as? ObjectNode)?.set<JsonNode>(
            "candidate_model_ids",
            candidateModelIdsToArrayNode(jsonMapper, candidateModelIds)
        )
    }

    /**
     * Builds a JSON array node from a list of candidate model ids.
     *
     * @param jsonMapper The Jackson ObjectMapper to use for JSON manipulation
     * @param candidateModelIds Candidate model ids to include in the array
     * @return JSON array of integer ids
     */
    private fun candidateModelIdsToArrayNode(
        jsonMapper: ObjectMapper,
        candidateModelIds: List<Int>
    ): ArrayNode = jsonMapper.createArrayNode().also { arrayNode ->
        candidateModelIds.forEach { arrayNode.add(it) }
    }

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
