/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

// TODO: Get for selector.
@CommandLine.Command(
    name = "selector",
    description = ["Manage selectors"],
    subcommands = [ModelSelectorListCommand::class, ModelSelectorCreateCommand::class,
        ModelSelectorUpdateCommand::class, ModelSelectorDeleteCommand::class,
        ModelSelectorCloneCommand::class]
)
class ModelSelectorCommand : BaseSubCommand(), Callable<Int>