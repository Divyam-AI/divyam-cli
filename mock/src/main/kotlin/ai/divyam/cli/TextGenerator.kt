/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli

import kotlin.random.Random

class TextGenerator {
    private val subjects = listOf(
        "The scientist", "A curious student", "The professor", "My colleague",
        "The researcher", "An expert", "The author", "A philosopher",
        "The investigator", "A scholar", "The analyst", "A theorist"
    )

    private val verbs = listOf(
        "discovered", "analyzed", "investigated", "examined", "studied",
        "explored", "researched", "concluded", "determined", "observed",
        "revealed", "demonstrated", "proved", "showed", "found"
    )

    private val objects = listOf(
        "fascinating patterns",
        "complex relationships",
        "important findings",
        "significant results",
        "remarkable phenomena",
        "key insights",
        "valuable data",
        "critical evidence",
        "essential information",
        "surprising outcomes",
        "innovative solutions",
        "breakthrough discoveries"
    )

    private val connectors = listOf(
        "Furthermore", "Additionally", "Moreover", "However", "Nevertheless",
        "Consequently", "Therefore", "Meanwhile", "Subsequently", "Indeed",
        "In contrast", "Similarly", "Notably", "Importantly", "Ultimately"
    )

    private val descriptors = listOf(
        "thoroughly and systematically",
        "with great precision",
        "using advanced methods",
        "through careful observation",
        "via comprehensive analysis",
        "with remarkable accuracy",
        "employing sophisticated techniques",
        "through rigorous testing",
        "using innovative approaches",
        "with meticulous attention to detail",
        "through extensive research",
        "via detailed examination"
    )

    private val conclusions = listOf(
        "which has profound implications for future research",
        "leading to new understanding in the field",
        "opening up exciting possibilities for further study",
        "contributing significantly to our knowledge base",
        "providing valuable insights for practical applications",
        "establishing a foundation for future investigations",
        "revealing connections previously unknown to science",
        "demonstrating the complexity of natural phenomena"
    )

    fun generateSentence(): String {
        val subject = subjects.random()
        val verb = verbs.random()
        val obj = objects.random()
        val descriptor = descriptors.random()
        val conclusion = conclusions.random()

        return when (Random.nextInt(3)) {
            0 -> "$subject $verb $obj $descriptor."
            1 -> "$subject $verb $obj, $conclusion."
            else -> "$subject $verb $obj $descriptor, $conclusion."
        }
    }

    fun generateParagraph(sentenceCount: Int = Random.nextInt(4, 8)): String {
        val sentences = mutableListOf<String>()

        // First sentence
        sentences.add(generateSentence())

        // Additional sentences with connectors
        repeat(sentenceCount - 1) {
            val connector = connectors.random()
            val sentence = generateSentence().replaceFirst("The", "the")
                .replaceFirst("A ", "a ")
            sentences.add("$connector, $sentence")
        }

        return sentences.joinToString(" ")
    }

    fun generateText(
        paragraphCount: Int = 5,
        sentencesPerParagraph: Int = 6
    ): String {
        val paragraphs = mutableListOf<String>()

        repeat(paragraphCount) {
            paragraphs.add(generateParagraph(sentencesPerParagraph))
        }

        return paragraphs.joinToString("\n\n")
    }
}
