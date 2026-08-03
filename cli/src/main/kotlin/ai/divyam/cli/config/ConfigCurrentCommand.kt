/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import ai.divyam.cli.base.OutputFormat
import ai.divyam.cli.format.Printing
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "current",
    description = ["Show the configuration currently in use"]
)
class ConfigCurrentCommand : Callable<Int> {
    @Option(
        names = ["--format"],
        description = [$$"output format. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    var outputFormat: OutputFormat = OutputFormat.JSON

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        val config = configCollection.getCurrentConfig()
        if (config == null) {
            System.err.println("No configuration is currently in use")
            return 1
        }
        if (outputFormat == OutputFormat.TEXT) {
            println("Current config: ${configCollection.currentConfigName}")
            Printing.printObjs(config, outputFormat)
        } else {
            Printing.printObjs(
                mapOf(
                    "currentConfigName" to configCollection.currentConfigName,
                    "config" to config
                ),
                outputFormat
            )
        }
        return 0
    }
}
