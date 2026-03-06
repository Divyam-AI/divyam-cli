/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "config",
    description = ["Manage default configuration - for e.g. credentials, " +
            "default org, service account etc."],
    subcommands = [ConfigSetCommand::class, ConfigGetCommand::class,
        ConfigUseCommand::class, ConfigListCommand::class, ConfigUnsetCommand::class],
)
class ConfigCommand : BaseSubCommand(), Callable<Int>