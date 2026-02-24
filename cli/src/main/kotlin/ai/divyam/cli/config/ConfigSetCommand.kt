/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(name = "set", description = ["Create org"])
class ConfigSetCommand : Callable<Int> {
    @Option(
        names = ["-c", "--config-name"],
        description = ["Unique name to identify the divyam cluster / endpoint" +
                ". e.g. acme-divyam-prod"],
        required = true
    )
    lateinit var configName: String

    @Option(
        names = ["-e", "--endpoint"],
        description = ["The API endpoint base URL (e.g. https://api.divyam" +
                ".ai)"],
    )
    var endpoint: String? = null

    @Option(
        names = ["--disable-tls-verification"],
        description = ["Disable TLS verification for non-production cases " +
                "only"],
        help = false,
        hidden = true
    )
    var disableTlsVerification: Boolean = false

    // TODO: Check all option names for usability, consistency and conventions.
    @Option(
        names = ["-u", "--user"],
        description = ["User email-id. Required if service account api token" +
                " is not provided."],
    )
    protected var user: String? = null

    @Option(
        names = ["-p", "--password"],
        description = ["User password. Required if service account api token " +
                "is not provided."],
        interactive = true,
        arity = "0..1"
    )
    protected var password: String? = null

    @Option(
        names = ["-t", "--api-token"],
        description = ["Service account api token"],
        interactive = true,
        arity = "0..1"
    )
    protected var apiToken: String? = null

    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "info with"]
    )
    private var orgId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Optional: service account id to associate the model " +
                "info with"],
    )
    private var serviceAccountId: String? = null

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        val oldConfig = configCollection.configs[configName]
        val newConfig = Config(
            endpoint = endpoint,
            disableTlsVerification = disableTlsVerification,
            user = user,
            password = password,
            apiToken = apiToken,
            orgId = orgId,
            serviceAccountId = serviceAccountId
        )
        val merged = oldConfig?.merge(newConfig) ?: newConfig
        configCollection.configs[configName] = merged
        configCollection.save()
        return 0
    }
}