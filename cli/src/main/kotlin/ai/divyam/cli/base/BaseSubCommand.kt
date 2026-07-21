/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.base

import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * Base class for top level sub command.
 */
abstract class BaseSubCommand : Callable<Int> {
    @Suppress("unused")
    @CommandLine.Option(
        names = ["--help"],
        usageHelp = true,
        description = ["display help message"]
    )
    private var helpRequested = false

    override fun call(): Int {
        CommandLine(this).usage(System.out)
        return 1
    }
}