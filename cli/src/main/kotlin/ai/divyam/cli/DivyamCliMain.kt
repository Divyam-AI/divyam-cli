/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli

import ai.divyam.cli.chat.ChatCommand
import ai.divyam.cli.debug.DebugCommand
import ai.divyam.cli.eval.EvalCommand
import ai.divyam.cli.model.ModelInfoCommand
import ai.divyam.cli.org.OrgCommand
import ai.divyam.cli.sa.SaCommand
import ai.divyam.cli.selector.ModelSelectorCommand
import ai.divyam.cli.user.UserCommand
import ai.divyam.cli.version.VersionCommand
import picocli.CommandLine
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "divyam",
    description = ["Divyam CLI"],
    subcommands = [OrgCommand::class, SaCommand::class, EvalCommand::class,
        ModelSelectorCommand::class, ModelInfoCommand::class,
        UserCommand::class, ChatCommand::class, DebugCommand::class, VersionCommand::class]
)
class DivyamCliMain : Callable<Int> {
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

fun main(args: Array<String>) {
    val rc =
        CommandLine(DivyamCliMain()).setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
    exitProcess(rc)
}