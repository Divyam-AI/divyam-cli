/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.SaSpecificCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(
    name = "ls",
    description = ["List the API keys of a service account, revoked ones included"]
)
class SaKeyListCommand : SaSpecificCommand() {
    override fun execute(): Int {
        runBlocking {
            val keys = divyamClient.listServiceAccountApiKeys(
                serviceAccountId = getSaId(serviceAccountId)
            )
            printObjs(keys)
        }
        return 0
    }
}
