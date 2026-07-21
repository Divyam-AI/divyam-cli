/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(name = "unset", description = ["Unset a config"])
class ConfigUnsetCommand : Callable<Int> {
    @Option(
        names = ["-c", "--config-name"],
        description = ["Unique name to identify the divyam cluster / endpoint" +
                ". e.g. acme-divyam-prod"],
        required = true
    )
    lateinit var configName: String

    @Option(
        names = ["-e", "--endpoint"],
        description = ["unset the API endpoint base URL"],
    )
    var endpoint: Boolean = false

    @Option(
        names = ["--disable-tls-verification"],
        description = ["unset the disable TLS verification flag"],
        hidden = true
    )
    var disableTlsVerification: Boolean = false

    @Option(
        names = ["-u", "--user"],
        description = ["unset the user email-id"],
    )
    protected var user: Boolean = false

    @Option(
        names = ["-p", "--password"],
        description = ["unset the user password"],
    )
    protected var password: Boolean = false

    @Option(
        names = ["-t", "--api-token"],
        description = ["unset the service account api token"],
    )
    protected var apiToken: Boolean = false

    @Option(
        names = ["-o", "--org-id"],
        description = ["unset the organization id"],
    )
    private var orgId: Boolean = false

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["unset the service account id"],
    )
    private var serviceAccountId: Boolean = false

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        val oldConfig = configCollection.configs[configName]

        if (oldConfig == null) {
            throw Exception("Config $configName not found")
        }

        val keysToRemove = mutableListOf<String>()
        if (endpoint) {
            keysToRemove.add("endpoint")
        }
        if (disableTlsVerification) {
            keysToRemove.add("disableTlsVerification")
        }
        if (user) {
            keysToRemove.add("user")
        }
        if (password) {
            keysToRemove.add("password")
        }
        if (apiToken) {
            keysToRemove.add("apiToken")
        }
        if (orgId) {
            keysToRemove.add("orgId")
        }
        if (serviceAccountId) {
            keysToRemove.add("serviceAccountId")
        }

        if (keysToRemove.isEmpty()) {
            throw Exception("Specify at least one option to unset")
        }

        val newConfig = oldConfig.unsetKeys(keysToRemove)
        configCollection.configs[configName] = newConfig
        configCollection.save()
        return 0
    }
}