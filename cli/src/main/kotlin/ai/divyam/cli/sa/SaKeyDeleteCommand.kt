/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.SaSpecificCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "delete",
    description = ["Delete a non-primary service account API key"]
)
class SaKeyDeleteCommand : SaSpecificCommand() {
    @Option(
        names = ["--key-id"],
        description = ["Service account API key id to delete"],
        required = true
    )
    private lateinit var keyId: String

    override fun execute(): Int {
        runBlocking {
            val response = divyamClient.deleteServiceAccountApiKey(
                serviceAccountId = getSaId(serviceAccountId),
                keyId = keyId
            )
            printObjs(response)
        }
        return 0
    }
}
