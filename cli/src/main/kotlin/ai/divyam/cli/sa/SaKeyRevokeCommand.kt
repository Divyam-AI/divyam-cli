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
    name = "revoke",
    description = ["Revoke an API key of a service account"]
)
class SaKeyRevokeCommand : SaSpecificCommand() {
    @Option(
        names = ["--key-id"],
        description = ["Required: id of the API key to revoke, from 'sa key ls'"],
        required = true
    )
    private lateinit var keyId: String

    override fun execute(): Int {
        runBlocking {
            divyamClient.revokeServiceAccountApiKey(
                serviceAccountId = getSaId(serviceAccountId),
                keyId = keyId
            )
        }
        println("Revoked API key $keyId")
        return 0
    }
}
