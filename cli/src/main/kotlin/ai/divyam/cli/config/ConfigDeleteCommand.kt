/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "delete",
    description = ["Delete a configuration"]
)
class ConfigDeleteCommand : Callable<Int> {
    @Option(
        names = ["-c", "--config-name"],
        description = ["The name of the configuration to delete"],
        required = true
    )
    lateinit var configName: String

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        if (configCollection.configs[configName] == null) {
            System.err.println("Config '$configName' not found")
            return 1
        }
        if (configCollection.currentConfigName == configName) {
            System.err.println("Cannot delete the current configuration. Pls switch to a different configuration first using 'divyam config use'")
            return 1
        }
        configCollection.configs.remove(configName)
        configCollection.save()
        println("Config '$configName' deleted successfully")
        return 0
    }
}