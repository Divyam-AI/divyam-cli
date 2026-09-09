/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorState
import io.ktor.client.request.forms.FormPart
import io.ktor.client.request.forms.InputProvider
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(
    name = "import",
    description = ["Import a selector from a bundle exported here or elsewhere"]
)
class ModelSelectorImportCommand : BaseCommand() {
    // A bundle is built, compressed and streamed before the first byte of the body arrives,
    // and how long that takes follows the selector's size. Hours rather than the engine's
    // default seconds, so the wait is bounded by the transfer and not by a limit meant for
    // calls that return a record.
    override val requestTimeoutMillis: Long = 2 * 60 * 60 * 1000L

    @Option(names = ["--file"], description = ["The bundle archive to import"], required = true)
    private var file: File? = null

    @Option(
        names = ["--name"],
        description = [
            "Name for the imported selector. Required: a bundle says what a selector is, " +
                "never where it belongs, and the name must be free across the organisation."
        ],
        required = true
    )
    private var name: String? = null

    @Option(
        names = ["--eval-id"],
        description = [
            "Use this existing eval instead of the one the bundle carries. Omitted, the " +
                "bundle's own eval is matched or created."
        ]
    )
    private var evalId: Int? = null


    @Option(
        names = ["--skip-benchmarks"],
        description = ["Do not replay the bundle's benchmark rows, leaving the dashboards without its history"]
    )
    private var skipBenchmarks: Boolean = false

    @Option(
        names = ["-o", "--org-id"],
        description = ["Organization id to import into. Falls back to DIVYAM_ORG_ID, then the current config file."]
    )
    private var orgId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Service account id to import into. Falls back to DIVYAM_SA_ID, then the current config file."]
    )
    private var serviceAccountId: String? = null

    override fun execute(): Int {
        val archive = requireNotNull(file) { "--file is required" }
        require(archive.isFile) { "No such bundle: ${archive.absolutePath}" }

        val imported = runBlocking {
            divyamClient.importModelSelector(
                archive = archivePart(archive),
                name = requireNotNull(name),
                orgId = getOrgId(orgId),
                serviceAccountId = getSaId(serviceAccountId),
                evalId = evalId,
                includeBenchmarks = !skipBenchmarks
            )
        }
        printObjs(imported, skipKeys = setOf("config", "endpoint"))
        return 0
    }

    /**
     * The archive as a streamed part, so that its size is never the client's heap.
     */
    private fun archivePart(archive: File): FormPart<InputProvider> = FormPart(
        "archive",
        InputProvider(archive.length()) { archive.inputStream().asInput() },
        Headers.build {
            append(HttpHeaders.ContentDisposition, "filename=\"${archive.name}\"")
            append(HttpHeaders.ContentType, "application/zip")
        }
    )
}
