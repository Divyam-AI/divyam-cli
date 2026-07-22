/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.format

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

object OutputRedactor {
    const val redactedValue = "[REDACTED]"

    fun redactJson(node: JsonNode, redactKeys: Set<String>): JsonNode {
        if (redactKeys.isEmpty()) return node

        return when {
            node.isObject -> redactObject(node.deepCopy<ObjectNode>(), redactKeys)
            node.isArray -> redactArray(node.deepCopy<ArrayNode>(), redactKeys)
            else -> node
        }
    }

    fun redactText(value: String, redactKeys: Set<String>): String {
        if (redactKeys.isEmpty()) return value

        val propertyPattern = Regex(
            "(\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*)([^,\\s)}\\]]+)"
        )

        return propertyPattern.replace(value) { match ->
            if (matchesKey(match.groupValues[2], redactKeys)) {
                "${match.groupValues[1]}$redactedValue"
            } else {
                match.value
            }
        }
    }

    fun matchesKey(key: String, redactKeys: Set<String>): Boolean {
        val normalizedKey = normalize(key)
        return redactKeys.any { normalize(it) == normalizedKey }
    }

    private fun redactObject(node: ObjectNode, redactKeys: Set<String>): ObjectNode {
        node.fieldNames().asSequence().toList().forEach { key ->
            if (matchesKey(key, redactKeys)) {
                node.set<JsonNode>(key, JsonNodeFactory.instance.textNode(redactedValue))
            } else {
                node.set<JsonNode>(key, redactJson(node.get(key), redactKeys))
            }
        }
        return node
    }

    private fun redactArray(node: ArrayNode, redactKeys: Set<String>): ArrayNode {
        for (index in 0 until node.size()) {
            node.set(index, redactJson(node.get(index), redactKeys))
        }
        return node
    }

    private fun normalize(key: String): String = key.filter(Char::isLetterOrDigit).lowercase()
}
