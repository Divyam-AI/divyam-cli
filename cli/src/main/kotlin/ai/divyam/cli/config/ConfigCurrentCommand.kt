/**
 * Copyright 2025-2026 DivyamAI Technologies Private Limited
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
    var outputFormat: OutputFormat = OutputFormat.TEXT

    override fun call(): Int {
        val configCollection = ConfigCollection.get()
        val config = configCollection.getCurrentConfig()
        if (config == null) {
            System.err.println("No configuration is currently in use")
            return 1
        }
        println("Current config: ${configCollection.currentConfigName}")
        Printing.printObjs(config, outputFormat)
        return 0
    }
}
