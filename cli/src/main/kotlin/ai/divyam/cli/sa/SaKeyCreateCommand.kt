/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.ServiceAccountApiKeyCreateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "create",
    description = ["Create an API key for a service account"]
)
class SaKeyCreateCommand : SaSpecificCommand() {
    @Option(
        names = ["--name"],
        description = ["Required: name to identify the new API key by"],
        required = true
    )
    private lateinit var name: String

    override fun execute(): Int {
        require(name.isNotBlank()) { "Key name must not be blank" }
        runBlocking {
            val issued = divyamClient.createServiceAccountApiKey(
                serviceAccountId = getSaId(serviceAccountId),
                serviceAccountApiKeyCreateRequest =
                    ServiceAccountApiKeyCreateRequest(name = name.trim())
            )

            printObjs(issued, flattenKeys = setOf("key"))
        }
        return 0
    }
}
