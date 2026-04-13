/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.data.model.RateLimitPolicy
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * Rate limit policy options shared by [SaCreateCommand] and [SaUpdateCommand].
 * Policy is a JSON array of objects with `provider_id`, `model_id`, `unit`, `duration`, and `limit`.
 */
data class RateLimitPolicyCliInput(
    val rateLimitPolicyFile: File?,
    val rateLimitPolicyInline: String?,
)

/**
 * Parses [RateLimitPolicy] list from a JSON file or inline JSON. Mutually exclusive sources.
 * Returns `null` when neither option is set (no change on update; omit on create).
 */
fun resolveRateLimitPolicy(
    mapper: ObjectMapper,
    input: RateLimitPolicyCliInput,
): List<RateLimitPolicy>? {
    val fromFile = input.rateLimitPolicyFile != null
    val fromInline = input.rateLimitPolicyInline != null
    if (fromFile && fromInline) {
        throw IllegalArgumentException(
            "Use only one of: --rate-limit-policy-file or --rate-limit-policy"
        )
    }
    val listTypeRef = object : TypeReference<List<RateLimitPolicy>>() {}
    input.rateLimitPolicyFile?.let { file ->
        if (file.exists()) {
            return mapper.readValue(file, listTypeRef)
        }
        throw IllegalArgumentException("Rate limit policy file not found: ${file.absolutePath}")
    }
    input.rateLimitPolicyInline?.let { json ->
        return mapper.readValue(json, listTypeRef)
    }
    return null
}
