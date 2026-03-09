/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.base

import ai.divyam.data.model.ServiceAccount
import picocli.CommandLine.Option

/**
 * A command that is tied to a specific service account.
 */
abstract class SaSpecificCommand : BaseCommand() {
    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Required: Service account id to associate with. If omitted, falls back to the DIVYAM_SA_ID environment variable, then the current config file."],
    )
    protected var serviceAccountId: String? = null

    suspend fun getServiceAccount(): ServiceAccount {
        return divyamClient.getServiceAccount(getSaId(serviceAccountId))
    }
}