/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.data.model.ModelSelectorCandidateModel
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility functions shared across selector commands.
 */
object SelectorCommandUtils {
    private val localDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val dottedOffsetPattern = Regex("([+-])(\\d{1,2})\\.(\\d{2})$")

    class TrainingWindowBoundary private constructor(
        val serializedValue: String,
        private val localDateTime: LocalDateTime?,
        private val instant: Instant?,
    ) {
        fun isAfter(other: TrainingWindowBoundary): Boolean = when {
            instant != null && other.instant != null -> instant.isAfter(other.instant)
            localDateTime != null && other.localDateTime != null -> localDateTime.isAfter(other.localDateTime)
            else -> throw IllegalArgumentException(
                "--start-date and --end-date must both include UTC offsets when either value includes one"
            )
        }

        companion object {
            fun parse(optionName: String, value: String, isEndBoundary: Boolean): TrainingWindowBoundary {
                val normalizedValue = normalizeDottedOffset(value)
                runCatching { LocalDate.parse(normalizedValue) }.getOrNull()?.let { date ->
                    val dateTime = if (isEndBoundary) {
                        date.atStartOfDay().plusDays(1).minusSeconds(1)
                    } else {
                        date.atStartOfDay()
                    }
                    return TrainingWindowBoundary(
                        serializedValue = dateTime.format(localDateTimeFormatter),
                        localDateTime = dateTime,
                        instant = null,
                    )
                }

                runCatching { OffsetDateTime.parse(normalizedValue) }.getOrNull()?.let { dateTime ->
                    return TrainingWindowBoundary(
                        serializedValue = dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        localDateTime = null,
                        instant = dateTime.toInstant(),
                    )
                }

                runCatching { LocalDateTime.parse(normalizedValue) }.getOrNull()?.let { dateTime ->
                    return TrainingWindowBoundary(
                        serializedValue = dateTime.format(localDateTimeFormatter),
                        localDateTime = dateTime,
                        instant = null,
                    )
                }

                throw IllegalArgumentException(
                    "$optionName must use YYYY-MM-DD or an ISO-8601 timestamp: $value"
                )
            }

            private fun normalizeDottedOffset(value: String): String =
                dottedOffsetPattern.replace(value) { match ->
                    "${match.groupValues[1]}${match.groupValues[2].padStart(2, '0')}:${match.groupValues[3]}"
                }
        }
    }

    /**
     * Applies explicit day boundaries to the training dataset source.
     *
     * A start date is inclusive from midnight and an end date is inclusive through
     * 23:59:59, matching selector clone dataset recreation semantics.
     */
    fun patchTrainDatasetDateRange(
        configNode: ObjectNode,
        startDate: TrainingWindowBoundary?,
        endDate: TrainingWindowBoundary?,
    ) {
        if (startDate == null && endDate == null) {
            return
        }

        require(startDate == null || endDate == null || !startDate.isAfter(endDate)) {
            "--start-date must be on or before --end-date"
        }

        val datasets = configNode.get("datasets") as? ObjectNode
            ?: throw IllegalArgumentException("config has no datasets")
        val trainDataset = datasets.get("train_ds") as? ObjectNode
            ?: throw IllegalArgumentException("config has no datasets.train_ds")
        val sourceSpecs = (trainDataset.get("source_specs") as? ObjectNode)
            ?: configNode.objectNode().also { trainDataset.set<ObjectNode>("source_specs", it) }

        startDate?.let { sourceSpecs.put("start_date", it.serializedValue) }
        endDate?.let { sourceSpecs.put("end_date", it.serializedValue) }
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
