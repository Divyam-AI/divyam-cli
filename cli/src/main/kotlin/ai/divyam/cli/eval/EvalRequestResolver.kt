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

/** The evaluator class the router uses to score through Evalm8. */
const val EVALM8_CLASS_NAME: String =
    "divyamlibs.evaluator.strategies.evalm8.evalm8_evaluation_criteria" +
        ".Evalm8RequestResponseEvaluationCriteria"

/** The default Evalm8 deployment, used when the caller does not name one. */
const val EVALM8_DEFAULT_BASE_URL: String = "https://evalm8.divyam.ai"

/** The Evalm8 constructor takes exactly these, and has no kwargs to absorb anything else. */
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
    /** True when any Evalm8 flag was passed, which is what selects the Evalm8 path. */
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
 * The eval an update should send.
 *
 * Every field is nullable because an update changes only what the caller named.
 * The router keeps its stored value for anything left out.
 */
data class ResolvedEvalUpdate(
    val name: String?,
    val state: EvalState?,
    val isPrimary: Boolean?,
    val granularity: EvalGranularity?,
    val className: String?,
    val classInitConfig: Map<String, Any?>?,
    val samplingConfig: Map<String, Any?>?,
)

/** The stored eval an update is being applied to, which is what partial Evalm8 flags merge onto. */
data class ExistingEval(
    val className: String?,
    val classInitConfig: Map<String, Any?>?,
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
                listOf(
                    "--class-name and the --evalm8-* flags both name the evaluator class.",
                    "  Use the --evalm8-* flags for an eval defined in Evalm8.",
                    "  Use --class-name for one of the router's built-in evaluators.",
                ).joinToString("\n"),
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

    /**
     * The same resolution as [resolve], for a partial update.
     *
     * Two things differ. Nothing is required, since an update names only what it changes.
     * The Evalm8 flags also merge onto the stored config rather than starting empty.
     * Rotating one identifier such as the api key therefore keeps the rest of them.
     */
    @Suppress("LongParameterList")
    fun resolveUpdate(
        evalConfig: String?,
        evalConfigFile: File?,
        name: String?,
        state: EvalState?,
        isPrimary: Boolean?,
        granularity: EvalGranularity?,
        className: String?,
        classInitConfig: String?,
        evalm8: Evalm8Options,
        existing: ExistingEval,
    ): ResolvedEvalUpdate {
        if (evalConfig != null && evalConfigFile != null) {
            throw IllegalArgumentException("Use only one of: --eval-config, --eval-config-file.")
        }
        if (className != null && evalm8.anyProvided()) {
            throw IllegalArgumentException(
                listOf(
                    "--class-name and the --evalm8-* flags both name the evaluator class.",
                    "  Use the --evalm8-* flags for an eval defined in Evalm8.",
                    "  Use --class-name for one of the router's built-in evaluators.",
                ).joinToString("\n"),
            )
        }

        val tree = readDocument(evalConfig, evalConfigFile)
        applyOverrides(tree, name, state, isPrimary, granularity, className, classInitConfig)
        if (evalm8.anyProvided()) {
            seedFromExisting(tree, existing)
            applyEvalm8(tree, evalm8)
        }

        requireConfigDecisionWhenLeavingEvalm8(tree, existing)
        validateForUpdate(tree)
        return toResolvedEvalUpdate(tree)
    }

    /**
     * Refuses to move an eval off Evalm8 while leaving the Evalm8 config attached.
     *
     * An absent class_init_config means no change, so the six Evalm8 identifiers would stay on an eval whose new class accepts none of them.
     * The evaluator factory splats the stored config into the constructor, so that eval raises TypeError and is dropped from the active set without surfacing anything.
     * The caller has to say what the new class should be built with, even when the answer is nothing.
     */
    private fun requireConfigDecisionWhenLeavingEvalm8(tree: ObjectNode, existing: ExistingEval) {
        if (existing.className != EVALM8_CLASS_NAME) return
        if (existing.classInitConfig.isNullOrEmpty()) return
        if (tree.has("class_init_config")) return
        val target = tree.get("class_name")?.asText() ?: return
        if (target == EVALM8_CLASS_NAME) return

        throw IllegalArgumentException(
            listOf(
                "This eval is moving off Evalm8, so its Evalm8 config cannot stay attached.",
                "  Leaving it would keep ${existing.classInitConfig.keys.sorted().joinToString(", ")} on an eval whose class takes none of them, and it would stop scoring.",
                "  Pass --class-init-config '{}' to clear it.",
                "  Pass --class-init-config '{...}' to give ${target.substringAfterLast('.')} the arguments it needs.",
            ).joinToString("\n"),
        )
    }

    /**
     * Puts the stored Evalm8 config under the flags, so a partial change keeps the identifiers it did not name.
     *
     * Only an eval that is already an Evalm8 eval is merged onto.
     * Converting some other evaluator to Evalm8 starts from nothing, so the caller has to name every identifier and is told which are missing.
     */
    private fun seedFromExisting(tree: ObjectNode, existing: ExistingEval) {
        if (existing.className != EVALM8_CLASS_NAME) return
        if (tree.has("class_init_config")) return
        val stored = existing.classInitConfig ?: return
        tree.replace("class_init_config", mapper.valueToTree(stored))
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

    /** Folds the Evalm8 flags into the config, naming the class the caller no longer has to know. */
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
                listOf(
                    "An evaluator is required, and none was named.",
                    "  Name one in Evalm8 with --evalm8-org, --evalm8-project and --evalm8-eval-name.",
                    "  Or supply a whole eval with --eval-config-file.",
                ).joinToString("\n"),
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

    /**
     * The update flavour of [validate].
     *
     * Nothing is required, since an update changes only what it names.
     * What is present is held to the same rules, so an update cannot write a config that a create would have refused.
     */
    private fun validateForUpdate(tree: ObjectNode) {
        val nameNode = tree.get("name")
        if (nameNode != null && nameNode.asText().isBlank()) {
            throw IllegalArgumentException("--name cannot be blank.")
        }

        val configNode = tree.get("class_init_config")
        if (configNode != null && !configNode.isNull && configNode !is ObjectNode) {
            throw IllegalArgumentException("class_init_config must be a JSON object.")
        }
        val config = configNode as? ObjectNode ?: return

        val owned = config.fieldNames().asSequence().filter { it in FACTORY_OWNED_KEYS }.toList()
        if (owned.isNotEmpty()) {
            throw IllegalArgumentException(
                "class_init_config must not set ${owned.joinToString(", ")}, because the evaluator " +
                    "factory supplies these and would overwrite them.",
            )
        }

        if (tree.get("class_name")?.asText() == EVALM8_CLASS_NAME) {
            validateEvalm8Config(config)
        }
    }

    private fun validateEvalm8Config(config: ObjectNode) {
        val missing = EVALM8_REQUIRED_KEYS.filter { config.get(it)?.asText().isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException(
                listOf(
                    "Registering an Evalm8 eval needs every one of its identifiers.",
                    "  Missing: ${missing.joinToString(", ") { FLAG_FOR_KEY[it] ?: it }}",
                ).joinToString("\n"),
            )
        }
        val allowed = EVALM8_REQUIRED_KEYS + EVALM8_OPTIONAL_KEYS
        val unknown = config.fieldNames().asSequence().filterNot { it in allowed }.toList()
        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException(
                listOf(
                    "class_init_config for the Evalm8 evaluator has keys it does not accept.",
                    "  Unknown: ${unknown.joinToString(", ")}",
                    "  Allowed: ${allowed.joinToString(", ")}",
                ).joinToString("\n"),
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

    private fun toResolvedEvalUpdate(tree: ObjectNode): ResolvedEvalUpdate = ResolvedEvalUpdate(
        name = tree.get("name")?.asText(),
        state = tree.get("state")?.asText()?.let { raw ->
            EvalState.decode(raw) ?: throw IllegalArgumentException(
                "Unknown state '$raw'. Valid values: ${EvalState.values().joinToString(", ")}.",
            )
        },
        isPrimary = tree.get("is_primary")?.asBoolean(),
        granularity = tree.get("granularity")?.asText()?.let { raw ->
            EvalGranularity.decode(raw) ?: throw IllegalArgumentException(
                "Unknown granularity '$raw'. Valid values: " +
                    "${EvalGranularity.values().joinToString(", ")}.",
            )
        },
        className = tree.get("class_name")?.asText(),
        classInitConfig = asMap(tree.get("class_init_config")),
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
