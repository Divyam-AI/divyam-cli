/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset

@CommandLine.Command(
    name = "export",
    description = ["Export a selector as a bundle that can be imported elsewhere"]
)
class ModelSelectorExportCommand : BaseCommand() {
    // A bundle is built, compressed and streamed before the first byte of the body arrives,
    // and how long that takes follows the selector's size. Hours rather than the engine's
    // default seconds, so the wait is bounded by the transfer and not by a limit meant for
    // calls that return a record.
    override val requestTimeoutMillis: Long = 2 * 60 * 60 * 1000L

    @Option(names = ["--id"], description = ["The model selector id to export"], required = true)
    private var id: Int = 0

    @Option(
        names = ["--out"],
        description = ["Where to write the archive. Defaults to a name from the selector and the time."]
    )
    private var out: File? = null

    @Option(
        names = ["--include-dataset"],
        description = [
            "Include the training dataset. Left out by default: it is customer traffic, " +
                "and nothing needs it in order to serve."
        ]
    )
    private var includeDataset: Boolean = false

    override fun execute(): Int {
        val target = out ?: File(defaultName())
        // Copied off the response channel rather than taken as a return value: an archive is
        // arbitrarily large, and the generated client would have to hold one whole to hand it
        // over -- and has no way to turn an `application/zip` body into a file at all.
        val written = runBlocking {
            val response = divyamClient.exportModelSelectorWithResponse(
                id,
                includeDataset,
                extraHeaders = mapOf("Accept" to listOf("application/zip")),
            )
            if (response.status != 200) {
                val detail = runCatching { response.response.bodyAsText() }.getOrDefault("")
                System.err.println("Export failed: HTTP ${response.status} $detail")
                return@runBlocking -1L
            }
            response.response.bodyAsChannel().toInputStream().use { source ->
                target.outputStream().use { sink -> source.copyTo(sink) }
            }
        }
        if (written < 0) {
            return 1
        }
        println("Wrote ${target.absolutePath} (${target.length()} bytes)")
        return 0
    }

    private fun defaultName(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        return "selector-$id-$stamp.zip"
    }
}
