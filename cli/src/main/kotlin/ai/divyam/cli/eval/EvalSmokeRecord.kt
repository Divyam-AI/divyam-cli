/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import com.fasterxml.jackson.annotation.JsonProperty

data class EvalSmokeRecord(
    val id: String,
    @get:JsonProperty("response_id")
    val responseId: String,
    val timestamp: String,
    @get:JsonProperty("traffic_bucket")
    val trafficBucket: String,
    @get:JsonProperty("requested_model")
    val requestedModel: String,
    val request: Map<String, Any>,
    val response: Map<String, Any>,
    @get:JsonProperty("requested_model_provider")
    val requestedModelProvider: String? = null,
)
