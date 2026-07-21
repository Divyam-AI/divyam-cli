/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.model

import ai.divyam.cli.base.BaseSubCommand
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "model-info",
    description = ["Manage model info."],
    subcommands = [ModelInfoListCommand::class, ModelInfoCreateCommand::class,
        ModelInfoUpdateCommand::class, ModelInfoGetCommand::class, ModelInfoDeleteCommand::class]
)
class ModelInfoCommand : BaseSubCommand(), Callable<Int> {
    companion object {
        fun parseJson(json: String, mapper: ObjectMapper): Any? {
            val rootNode: JsonNode = mapper.readTree(json)

            return processNode(rootNode)
        }

        fun processNode(node: JsonNode): Any? {
            return when {
                node.isObject -> {
                    val result = mutableMapOf<Any, Any?>()
                    // Iterate over the key-value pairs of the object
                    node.properties().forEach { (key, value) ->
                        result[key] = processNode(value)
                    }
                    result
                }

                node.isArray -> {
                    val result = mutableListOf<Any?>()

                    // Iterate over the elements of the list
                    node.forEach { element ->
                        result.add(processNode(element))
                    }

                    result
                }

                node.isTextual -> {
                    node.asText()
                }

                node.isNumber -> {
                    node.numberValue()
                }

                node.isBoolean -> {
                    node.asBoolean()
                }

                node.isNull -> {
                    null
                }

                else -> {
                    throw Exception("Unknown type or empty node $node")
                }
            }
        }
    }
}