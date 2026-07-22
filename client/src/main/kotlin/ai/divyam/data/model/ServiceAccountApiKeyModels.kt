/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.data.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ServiceAccountApiKeyRecord(
    @param:JsonProperty("id")
    val id: String,
    @param:JsonProperty("service_account_id")
    val serviceAccountId: String,
    @param:JsonProperty("is_primary")
    val isPrimary: Boolean,
    @param:JsonProperty("created_at")
    val createdAt: Int
)

data class IssuedServiceAccountApiKey(
    @param:JsonProperty("key")
    val key: ServiceAccountApiKeyRecord,
    @param:JsonProperty("api_key")
    val apiKey: String
)

data class ServiceAccountApiKeyDeleteResponse(
    @param:JsonProperty("detail")
    val detail: String
)
