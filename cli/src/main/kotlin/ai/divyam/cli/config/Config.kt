/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.config

import ai.divyam.cli.format.Printing
import kotlinx.io.IOException
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

private val mapper = Printing.getJsonMapper()

data class Config(
    val endpoint: String?,
    val user: String? = null,
    val password: String? = null,
    val apiToken: String? = null,
    val orgId: Int? = null,
    val serviceAccountId: String? = null,
    var disableTlsVerification: Boolean = false,
) {
    fun merge(other: Config): Config {
        val thisAsMap = Config::class.memberProperties.associate {
            it.name to it
                .get(this)
        }
        val otherAsMap =
            Config::class.memberProperties.associate { it.name to it.get(other) }
        val mergedMap = thisAsMap + otherAsMap.filterValues { it != null }
        val constructor = Config::class.primaryConstructor
        val merged = constructor!!.let { cons ->
            val args = cons.parameters.associateWith { param ->
                mergedMap[param
                    .name]
            }
            cons.callBy(args)
        }
        return merged
    }
}

data class ConfigCollection(
    val configs: MutableMap<String, Config> = HashMap(),
    var currentConfigName: String? = null
) {
    companion object {
        val homePath = Paths.get(System.getProperty("user.home"))
        val configFolder = homePath.resolve(".divyam")
        val configFile = configFolder.resolve("config.json")

        fun get(): ConfigCollection {
            return try {
                try {
                    if (!configFolder.exists()) {
                        configFolder.createDirectories()
                    }
                } catch (ex: IOException) {
                    System.err.println("Could not create config directory - ${ex.message}")
                }

                if (!configFile.exists()) {
                    configFile.createFile()
                }

                mapper.readValue(
                    configFile.readText(),
                    ConfigCollection::class.java
                )
            } catch (ex: Exception) {
                System.err.println("Could not read divyam config directory - ${ex.message}")
                ConfigCollection()
            }
        }
    }

    fun save() {
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            configFile.toFile(),
            this
        )
    }

    fun getCurrentConfig(): Config? {
        if (currentConfigName == null) {
            if (configs.size == 1) {
                return configs.values.first()
            }
            return null
        }
        return configs[currentConfigName]
    }
}
