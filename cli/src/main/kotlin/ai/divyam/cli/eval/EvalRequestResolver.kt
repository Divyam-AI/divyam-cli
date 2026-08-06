/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.data.model.EvalGranularity
import ai.divyam.data.model.EvalState
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File

/** The evaluator class the router uses to score through evalm8. */
const val EVALM8_CLASS_NAME: String =
    "divyamlibs.evaluator.strategies.evalm8.evalm8_evaluation_criteria" +
        ".Evalm8RequestResponseEvaluationCriteria"

/** The default evalm8 deployment, used when the caller does not name one. */
const val EVALM8_DEFAULT_BASE_URL: String = "https://evalm8.divyam.ai"

/** The evalm8 constructor takes exactly these, and has no kwargs to absorb anything else. */
private val EVALM8_REQUIRED_KEYS = listOf("base_url", "api_key", "org", "project", "eval_name")
private val EVALM8_OPTIONAL_KEYS = listOf("eval_ref")

/**
 * Keys the evaluator factory injects itself, overwriting whatever was stored.
 *
 * Accepting one would silently discard the caller's value at scoring time, so reject it here.
 */
private val FACTORY_OWNED_KEYS =
    listOf("name", "eval_id", "eval_sampling_config", "decrypted_provider_info_list")

/** What the caller asked for through the --evalm8-* flags. */
data class Evalm8Options(
    val org: String? = null,
    val project: String? = null,
    val evalName: String? = null,
    val evalRef: String? = null,
    val baseUrl: String? = null,
    val apiKey: String? = null,
) {
    /** True when any evalm8 flag was passed, which is what selects the evalm8 path. */
    fun anyProvided(): Boolean =
        listOf(org, project, evalName, evalRef, baseUrl, apiKey).any { it != null }
}

/** A complete eval, ready for the parts only the command knows (org id and service account). */
data class ResolvedEval(
    val name: String,
    val state: EvalState,
    val isPrimary: Boolean,
    val granularity: EvalGranularity?,
    val className: String,
    val classInitConfig: Map<String, Any?>,
    val samplingConfig: Map<String, Any?>?,
)

/**
 * Turns the three input modes into one eval.
 *
 * A config document supplies the base, explicit flags override it, and defaults fill the rest.
 * Precedence is therefore flag > document > default.
 * SelectorConfigBuilder documents the same order for selectors.
 */
class EvalRequestResolver(private val mapper: ObjectMapper) {

    @Suppress("LongParameterList")
    fun resolve(
        evalConfig: String?,
        evalConfigFile: File?,
        name: String?,
        state: EvalState?,
        isPrimary: Boolean?,
        granularity: EvalGranularity?,
        className: String?,
        classInitConfig: String?,
        evalm8: Evalm8Options,
    ): ResolvedEval {
        if (evalConfig != null && evalConfigFile != null) {
            throw IllegalArgumentException("Use only one of: --eval-config, --eval-config-file.")
        }
        if (className != null && evalm8.anyProvided()) {
            throw IllegalArgumentException(
                "--class-name cannot be combined with the --evalm8-* flags, since both name the " +
                    "evaluator class. Use one or the other.",
            )
        }

        val tree = readDocument(evalConfig, evalConfigFile)
        applyOverrides(tree, name, state, isPrimary, granularity, className, classInitConfig)
        if (evalm8.anyProvided()) {
            applyEvalm8(tree, evalm8)
        }

        validate(tree)
        return toResolvedEval(tree)
    }

    private fun readDocument(evalConfig: String?, evalConfigFile: File?): ObjectNode {
        val raw = when {
            evalConfigFile != null -> {
                if (!evalConfigFile.isFile) {
                    throw IllegalArgumentException(
                        "Eval config file does not exist: ${evalConfigFile.absolutePath}",
                    )
                }
                runCatching { mapper.readTree(evalConfigFile) }.getOrElse {
                    throw IllegalArgumentException(
                        "Could not parse ${evalConfigFile.absolutePath} as JSON: ${it.message}",
                    )
                }
            }

            evalConfig != null -> runCatching { mapper.readTree(evalConfig) }.getOrElse {
                throw IllegalArgumentException("Could not parse --eval-config as JSON: ${it.message}")
            }

            else -> return mapper.createObjectNode()
        }
        return raw as? ObjectNode
            ?: throw IllegalArgumentException("The eval config must be a JSON object.")
    }

    @Suppress("LongParameterList")
    private fun applyOverrides(
        tree: ObjectNode,
        name: String?,
        state: EvalState?,
        isPrimary: Boolean?,
        granularity: EvalGranularity?,
        className: String?,
        classInitConfig: String?,
    ) {
        name?.let { tree.put("name", it) }
        state?.let { tree.put("state", it.value) }
        isPrimary?.let { tree.put("is_primary", it) }
        granularity?.let { tree.put("granularity", it.value) }
        className?.let { tree.put("class_name", it) }
        classInitConfig?.let { tree.replace("class_init_config", parseInitConfig(it)) }
    }

    private fun parseInitConfig(raw: String): ObjectNode {
        val parsed = runCatching { mapper.readTree(raw) }.getOrElse {
            throw IllegalArgumentException("Could not parse --class-init-config as JSON: ${it.message}")
        }
        return parsed as? ObjectNode
            ?: throw IllegalArgumentException("--class-init-config must be a JSON object.")
    }

    /** Folds the evalm8 flags into the config, naming the class the caller no longer has to know. */
    private fun applyEvalm8(tree: ObjectNode, evalm8: Evalm8Options) {
        val existing = tree.get("class_name")?.asText()
        if (existing != null && existing != EVALM8_CLASS_NAME) {
            throw IllegalArgumentException(
                "The eval config names class '$existing', but the --evalm8-* flags select " +
                    "$EVALM8_CLASS_NAME. Remove one of them.",
            )
        }
        tree.put("class_name", EVALM8_CLASS_NAME)

        val config = tree.get("class_init_config") as? ObjectNode ?: mapper.createObjectNode()
        evalm8.org?.let { config.put("org", it) }
        evalm8.project?.let { config.put("project", it) }
        evalm8.evalName?.let { config.put("eval_name", it) }
        evalm8.apiKey?.let { config.put("api_key", it) }
        config.put("eval_ref", evalm8.evalRef ?: config.get("eval_ref")?.asText() ?: "latest")
        config.put(
            "base_url",
            evalm8.baseUrl ?: config.get("base_url")?.asText() ?: EVALM8_DEFAULT_BASE_URL,
        )
        tree.replace("class_init_config", config)
    }

    private fun validate(tree: ObjectNode) {
        val name = tree.get("name")?.asText()
        if (name.isNullOrBlank()) {
            throw IllegalArgumentException("--name is required.")
        }
        val className = tree.get("class_name")?.asText()
        if (className.isNullOrBlank()) {
            throw IllegalArgumentException(
                "An evaluator is required. Name one in evalm8 with --evalm8-org, --evalm8-project " +
                    "and --evalm8-eval-name, or supply a full eval with --eval-config-file.",
            )
        }

        val configNode = tree.get("class_init_config")
        if (configNode != null && !configNode.isNull && configNode !is ObjectNode) {
            throw IllegalArgumentException("class_init_config must be a JSON object.")
        }
        val config = configNode as? ObjectNode ?: mapper.createObjectNode()

        val owned = config.fieldNames().asSequence().filter { it in FACTORY_OWNED_KEYS }.toList()
        if (owned.isNotEmpty()) {
            throw IllegalArgumentException(
                "class_init_config must not set ${owned.joinToString(", ")}, because the evaluator " +
                    "factory supplies these and would overwrite them.",
            )
        }

        if (className == EVALM8_CLASS_NAME) {
            validateEvalm8Config(config)
        }
    }

    private fun validateEvalm8Config(config: ObjectNode) {
        val missing = EVALM8_REQUIRED_KEYS.filter { config.get(it)?.asText().isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException(
                missing.joinToString(", ") { FLAG_FOR_KEY[it] ?: it } +
                    " ${if (missing.size == 1) "is" else "are"} required when registering an " +
                    "evalm8 eval.",
            )
        }
        val allowed = EVALM8_REQUIRED_KEYS + EVALM8_OPTIONAL_KEYS
        val unknown = config.fieldNames().asSequence().filterNot { it in allowed }.toList()
        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException(
                "class_init_config for the evalm8 evaluator does not accept: " +
                    "${unknown.joinToString(", ")}. Allowed keys: ${allowed.joinToString(", ")}.",
            )
        }
    }

    private fun toResolvedEval(tree: ObjectNode): ResolvedEval = ResolvedEval(
        name = tree.get("name").asText(),
        // Decoding returns null on an unrecognised value, so reject it rather than fall back to a default.
        state = tree.get("state")?.asText()?.let { raw ->
            EvalState.decode(raw) ?: throw IllegalArgumentException(
                "Unknown state '$raw'. Valid values: ${EvalState.values().joinToString(", ")}.",
            )
        } ?: EvalState.ACTIVE,
        isPrimary = tree.get("is_primary")?.asBoolean() ?: false,
        granularity = tree.get("granularity")?.asText()?.let { raw ->
            EvalGranularity.decode(raw) ?: throw IllegalArgumentException(
                "Unknown granularity '$raw'. Valid values: " +
                    "${EvalGranularity.values().joinToString(", ")}.",
            )
        },
        className = tree.get("class_name").asText(),
        classInitConfig = asMap(tree.get("class_init_config")) ?: emptyMap(),
        samplingConfig = asMap(tree.get("sampling_config")),
    )

    private fun asMap(node: JsonNode?): Map<String, Any?>? {
        if (node == null || node.isNull) return null
        @Suppress("UNCHECKED_CAST")
        return mapper.convertValue(node, Map::class.java) as Map<String, Any?>
    }

    private companion object {
        /** Named so a missing value points at the flag to pass, not at an internal config key. */
        val FLAG_FOR_KEY = mapOf(
            "org" to "--evalm8-org",
            "project" to "--evalm8-project",
            "eval_name" to "--evalm8-eval-name",
            "api_key" to "--evalm8-api-key",
            "base_url" to "--evalm8-base-url",
        )
    }
}
