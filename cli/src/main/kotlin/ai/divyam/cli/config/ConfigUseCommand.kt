/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "use", description = ["Use a configuration for " +
            "all subsequent commands"]
)
class ConfigUseCommand : Callable<Int> {
    @Option(
        names = ["-c", "--config-name"],
        description = ["Unique name to identify the divyam cluster / endpoint" +
                ". e.g. acme-divyam-prod"],
        required = true
    )
    lateinit var configName: String

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        if (!configCollection.configs.contains(configName)) {
            System.err.println("Config '$configName' not found")
            return 1
        }
        configCollection.currentConfigName = configName
        configCollection.save()
        return 0
    }
}