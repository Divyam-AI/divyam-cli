package ai.divyam.cli.selector

import ai.divyam.data.model.ModelSelectorCandidateModel

/**
 * Utility functions shared across selector commands.
 */
object SelectorCommandUtils {
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
