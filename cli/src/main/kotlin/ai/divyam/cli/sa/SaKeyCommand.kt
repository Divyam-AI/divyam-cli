/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "key",
    description = ["Manage service account API keys"],
    subcommands = [
        SaKeyListCommand::class,
        SaKeyCreateCommand::class,
        SaKeyDeleteCommand::class
    ]
)
class SaKeyCommand : BaseSubCommand(), Callable<Int>
