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
    name = "get",
    description = ["Get details for a configuration"]
)
class ConfigGetCommand : Callable<Int> {
    @Option(
        names = ["-c", "--config-name"],
        description = ["Unique name to identify the divyam cluster / endpoint" +
                ". e.g. acme-divyam-prod"],
        required = true
    )
    lateinit var configName: String

    @Option(
        names = ["--format"],
        description = [$$"output format. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    var outputFormat: OutputFormat = OutputFormat.TEXT

    override fun call(): Int {
        val config = ConfigCollection.get().configs[configName]
        if (config == null) {
            System.err.println("Config '$configName' not found")
            return 1
        }
        Printing.printObjs(config, outputFormat)
        return 0
    }
}