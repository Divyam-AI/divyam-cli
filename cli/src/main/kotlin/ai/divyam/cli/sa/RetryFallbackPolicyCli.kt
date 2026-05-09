/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.data.model.RetryFallbackPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * Retry/fallback policy options shared by [SaCreateCommand] and [SaUpdateCommand].
 */
data class RetryFallbackPolicyCliInput(
    val retryFallbackPolicyFile: File?,
    val retryFallbackPolicyInline: String?,
    val retryDelayS: Int?,
    val retryDelayMultiplier: Int?,
    val maxRetries: Int?,
    val maxRequestLatencyS: Int?,
    val maxFallbackHops: Int?,
    val circuitBreakerRequestThreshold: Int?,
    val circuitBreakerFailureThresholdPct: Int?,
    val circuitBreakerDurationS: Int?,
    val circuitBreakerSlidingWindowTimeS: Int?,
    val circuitBreakerSlidingWindowResolutionS: Int?,
)

fun RetryFallbackPolicyCliInput.hasAnyRetryFallbackIndividualArg(): Boolean =
    retryDelayS != null || retryDelayMultiplier != null || maxRetries != null ||
        maxRequestLatencyS != null || maxFallbackHops != null ||
        circuitBreakerRequestThreshold != null || circuitBreakerFailureThresholdPct != null ||
        circuitBreakerDurationS != null || circuitBreakerSlidingWindowTimeS != null ||
        circuitBreakerSlidingWindowResolutionS != null

/**
 * Resolves retry/fallback policy from exactly one source: file, inline JSON, or individual
 * arguments (mutually exclusive).
 *
 * When resolving from individual arguments, each set field overrides [baseRetryPolicy]; pass
 * `null` for [baseRetryPolicy] on create so unspecified fields stay null.
 * File and inline JSON replace the policy entirely.
 */
fun resolveRetryFallbackPolicy(
    mapper: ObjectMapper,
    input: RetryFallbackPolicyCliInput,
    baseRetryPolicy: RetryFallbackPolicy?,
): RetryFallbackPolicy? {
    val fromFile = input.retryFallbackPolicyFile != null
    val fromInline = input.retryFallbackPolicyInline != null
    val fromIndividual = input.hasAnyRetryFallbackIndividualArg()
    when {
        fromFile && (fromInline || fromIndividual) ->
            throw IllegalArgumentException(
                "Use only one of: --retry-fallback-policy-file, --retry-fallback-policy, or " +
                    "individual --retry-*/--circuit-breaker-* arguments"
            )
        fromInline && fromIndividual ->
            throw IllegalArgumentException(
                "Use only one of: --retry-fallback-policy-file, --retry-fallback-policy, or " +
                    "individual --retry-*/--circuit-breaker-* arguments"
            )
    }
    input.retryFallbackPolicyFile?.let { file ->
        if (file.exists()) {
            return mapper.readValue(file, RetryFallbackPolicy::class.java)
        }
        throw IllegalArgumentException("Retry fallback policy file not found: ${file.absolutePath}")
    }
    input.retryFallbackPolicyInline?.let { json ->
        return mapper.readValue(json, RetryFallbackPolicy::class.java)
    }
    if (fromIndividual) {
        val base = baseRetryPolicy ?: RetryFallbackPolicy()
        return RetryFallbackPolicy(
            retryDelayS = input.retryDelayS ?: base.retryDelayS,
            retryDelayMultiplier = input.retryDelayMultiplier ?: base.retryDelayMultiplier,
            maxRetries = input.maxRetries ?: base.maxRetries,
            maxRequestLatencyS = input.maxRequestLatencyS ?: base.maxRequestLatencyS,
            maxFallbackHops = input.maxFallbackHops ?: base.maxFallbackHops,
            circuitBreakerRequestThreshold =
                input.circuitBreakerRequestThreshold ?: base.circuitBreakerRequestThreshold,
            circuitBreakerFailureThresholdPct =
                input.circuitBreakerFailureThresholdPct ?: base.circuitBreakerFailureThresholdPct,
            circuitBreakerDurationS = input.circuitBreakerDurationS ?: base.circuitBreakerDurationS,
            circuitBreakerSlidingWindowTimeS =
                input.circuitBreakerSlidingWindowTimeS ?: base.circuitBreakerSlidingWindowTimeS,
            circuitBreakerSlidingWindowResolutionS =
                input.circuitBreakerSlidingWindowResolutionS
                    ?: base.circuitBreakerSlidingWindowResolutionS,
        )
    }
    return null
}
