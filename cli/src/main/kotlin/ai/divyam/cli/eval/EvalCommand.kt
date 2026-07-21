/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "eval",
    description = ["Manage evals"],
    subcommands = [EvalListCommand::class, EvalCreateCommand::class,
        EvalUpdateCommand::class, EvalGetCommand::class]
)
class EvalCommand : BaseSubCommand(), Callable<Int>