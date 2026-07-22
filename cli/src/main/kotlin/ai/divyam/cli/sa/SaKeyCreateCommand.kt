/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.SaSpecificCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(
    name = "create",
    description = ["Create a new service account API key"]
)
class SaKeyCreateCommand : SaSpecificCommand() {
    override fun execute(): Int {
        runBlocking {
            val issuedKey = divyamClient.createServiceAccountApiKey(
                serviceAccountId = getSaId(serviceAccountId)
            )
            printObjs(issuedKey)
        }
        return 0
    }
}
