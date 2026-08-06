/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

/**
 * Confirms an eval exists in evalm8 before the router is told to use it.
 *
 * Without this the registration always succeeds, since the router never inspects class_init_config.
 * A wrong org, project, name or key then surfaces only at scoring time.
 * There the evaluator fails to construct and the eval drops silently out of the active set.
 */
class Evalm8Verifier {

    @Suppress("LongParameterList", "ThrowsCount")
    suspend fun verify(
        baseUrl: String,
        org: String,
        project: String,
        evalName: String,
        apiKey: String,
    ) {
        val url = URLBuilder(baseUrl)
            .appendPathSegments("api", "v1", "workspace", org, project, "evals", "evals", evalName)
            .buildString()

        val client = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MILLIS
                requestTimeoutMillis = TIMEOUT_MILLIS
            }
        }

        val (status, body) = client.use { http ->
            try {
                val response = http.get(url) { header(HttpHeaders.Authorization, "Bearer $apiKey") }
                response.status.value to response.bodyAsText()
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "Could not reach evalm8 at $baseUrl (${error.message}). " +
                        "Retry, or pass --skip-verify to register without checking.",
                )
            }
        }
        val reason = detailOf(body)

        when (status) {
            HTTP_OK -> return
            HTTP_NOT_FOUND -> throw IllegalArgumentException(
                "evalm8 has no eval named '$evalName' in org '$org' project '$project' at " +
                    "$baseUrl$reason Check --evalm8-eval-name, --evalm8-org and " +
                    "--evalm8-project, or pass --skip-verify to register without checking.",
            )

            // evalm8 answers a malformed, unknown or revoked key with 400, and a missing header with 401.
            // Retrying never helps for any of them, so they must not fall into the retry branch below.
            HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> throw IllegalArgumentException(
                "evalm8 rejected the api key at $baseUrl (HTTP $status)$reason " +
                    "Check --evalm8-api-key.",
            )

            else -> throw IllegalArgumentException(
                "evalm8 answered HTTP $status at $baseUrl$reason " +
                    "Retry, or pass --skip-verify to register without checking.",
            )
        }
    }

    /** evalm8 explains itself in a detail field, which is more use than the bare status. */
    private fun detailOf(body: String): String {
        val match = DETAIL.find(body) ?: return "."
        return ": ${match.groupValues[1]}"
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        val DETAIL = Regex("\"detail\"\\s*:\\s*\"([^\"]*)\"")
    }
}
