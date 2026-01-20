/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.version

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.Properties

@CommandLine.Command(
    name = "version",
    description = ["Show version information"]
)
class VersionCommand : BaseSubCommand() {

    override fun call(): Int {
        val buildInfo = loadBuildInfo()

        println("Divyam CLI")
        println("Version: ${buildInfo.getProperty("build.version")}")
        println("Commit: ${buildInfo.getProperty("git.commit")}")
        println("Build Time: ${buildInfo.getProperty("build.time")}")
        return 0
    }

    private fun loadBuildInfo(): Properties {
        return Properties().apply {
            try {
                load(VersionCommand::class.java.getResourceAsStream("/version.properties"))
            } catch (_: Exception) {
                setProperty("build.version", "unknown")
                setProperty("git.commit", "unknown")
                setProperty("build.time", "unknown")
            }
        }
    }
}