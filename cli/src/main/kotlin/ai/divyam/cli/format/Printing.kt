/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.format

import ai.divyam.cli.base.OutputFormat
import ai.divyam.cli.table.ObjectAsciiTablePrinter
import ai.divyam.client.DivyamClient
import ai.divyam.data.model.Input
import ai.divyam.data.model.InputDeserializer
import ai.divyam.data.model.InputSerializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object Printing {
    fun printJson(objs: Any) {
        val mapper = getJsonMapper()
        val writer = mapper.writerWithDefaultPrettyPrinter()
        println(writer.writeValueAsString(objs))
    }

    fun printObjs(
        objs: Any,
        outputFormat: OutputFormat,
        skipKeys: Set<String> = emptySet()
    ) {
        val sanitizedObjs = if (skipKeys.isEmpty()) {
            objs
        } else {
            removeRootKeys(objs, skipKeys)
        }
        when (outputFormat) {
            OutputFormat.TEXT -> ObjectAsciiTablePrinter.printTable(
                if (sanitizedObjs is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    sanitizedObjs as List<Any>
                } else {
                    listOf(sanitizedObjs)
                }
            )

            OutputFormat.JSON -> printJson(sanitizedObjs)
            OutputFormat.YAML -> printYaml(sanitizedObjs)
        }
    }

    private fun removeRootKeys(objs: Any, skipKeys: Set<String>): Any {
        val mapper = getJsonMapper()

        fun sanitize(node: JsonNode): JsonNode {
            if (!node.isObject) return node
            val objectNode = node.deepCopy<ObjectNode>()
            skipKeys.forEach { objectNode.remove(it) }
            return objectNode
        }

        return when (objs) {
            is List<*> -> objs.map {
                val node = mapper.valueToTree<JsonNode>(it)
                sanitize(node)
            }

            else -> {
                val node = mapper.valueToTree<JsonNode>(objs)
                sanitize(node)
            }
        }
    }

    fun getJsonMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        configureMapper(mapper)
        return mapper
    }

    private fun configureMapper(mapper: ObjectMapper) {
        mapper.registerKotlinModule()
        val module = SimpleModule().apply {
            addDeserializer(Input::class.java, InputDeserializer())
            addSerializer(Input::class.java, InputSerializer())
        }
        mapper.registerModule(module)

        mapper.apply { DivyamClient.configureObjectMapper()() }
    }

    fun printYaml(objs: Any) {
        val mapper = getYamlMapper()
        val writer = mapper.writerWithDefaultPrettyPrinter()
        println(writer.writeValueAsString(objs))
    }

    fun getYamlMapper(): YAMLMapper {
        val mapper = YAMLMapper()
        configureMapper(mapper)
        return mapper
    }
}