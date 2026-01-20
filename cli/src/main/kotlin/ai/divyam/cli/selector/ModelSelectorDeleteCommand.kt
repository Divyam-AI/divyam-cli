/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "delete", description = ["Delete a selector"])
class ModelSelectorDeleteCommand : BaseCommand() {
    @Option(
        names = ["--id"],
        description = ["The model selector id to delete"],
        required = true
    )
    private var id: Int = 0
    override fun execute(): Int {
        runBlocking {
            divyamClient.deleteModelSelector(
                modelSelectorId = id
            )
        }
        println("Deleted model selector id:$id")
        return 0
    }
}