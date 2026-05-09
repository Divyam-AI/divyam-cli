/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.model

import ai.divyam.data.model.RateLimitPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * Rate limit policy for [ModelInfoCreateCommand] / [ModelInfoUpdateCommand].
 * JSON matches [RateLimitPolicy]: `evaluation`, `training`, and `production` workload sections,
 * each with `unit`, `duration`, and `limit`.
 */
data class ModelRateLimitPolicyCliInput(
    val rateLimitPolicyFile: File?,
    val rateLimitPolicyInline: String?,
)

/**
 * Parses a [RateLimitPolicy] from a JSON file or inline JSON. Mutually exclusive sources.
 * Returns `null` when neither option is set (omit on create; no change on update).
 */
fun resolveModelRateLimitPolicy(
    mapper: ObjectMapper,
    input: ModelRateLimitPolicyCliInput,
): RateLimitPolicy? {
    val fromFile = input.rateLimitPolicyFile != null
    val fromInline = input.rateLimitPolicyInline != null
    if (fromFile && fromInline) {
        throw IllegalArgumentException(
            "Use only one of: --rate-limit-policy-file or --rate-limit-policy"
        )
    }
    input.rateLimitPolicyFile?.let { file ->
        if (file.exists()) {
            return mapper.readValue(file, RateLimitPolicy::class.java)
        }
        throw IllegalArgumentException("Rate limit policy file not found: ${file.absolutePath}")
    }
    input.rateLimitPolicyInline?.let { json ->
        return mapper.readValue(json, RateLimitPolicy::class.java)
    }
    return null
}
