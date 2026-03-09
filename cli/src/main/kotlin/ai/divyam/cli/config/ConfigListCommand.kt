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

@CommandLine.Command(name = "ls", description = ["List configurations"])
class ConfigListCommand : Callable<Int> {
    @Option(
        names = ["--format"],
        description = [$$"output format. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    var outputFormat: OutputFormat = OutputFormat.TEXT

    override fun call(): Int {
        val configNames = ConfigCollection.get().configs.keys.toList()
        Printing.printObjs(configNames, outputFormat)
        return 0
    }
}