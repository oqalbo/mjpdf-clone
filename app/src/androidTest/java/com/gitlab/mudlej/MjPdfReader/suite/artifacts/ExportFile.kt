// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File
import java.nio.charset.CodingErrorAction

data class ExportBlock(
    val infoLine: String,
    val lines: List<String>,
) {
    fun section(label: String): String? {
        val start = lines.indexOf("--- $label Start")
        if (start < 0) return null
        val end = lines.indexOf("--- $label End")
        if (end <= start) return null
        return lines.subList(start + 1, end).joinToString("\n")
    }

    fun quotePayload(): String? = section(TestEnvironment.targetContext.getString(R.string.export_quote_label))

    fun notePayload(): String? = section(TestEnvironment.targetContext.getString(R.string.note))
}

class ExportFile(val file: File) {

    val bytes: ByteArray = file.readBytes()

    val text: String = strictDecode(bytes)

    val lines: List<String> = text.split("\n")

    fun hasByteOrderMark(): Boolean = ArtifactBytes.startsWith(file, ArtifactBytes.UTF8_BOM)

    fun endsWithNewline(): Boolean = bytes.isNotEmpty() && bytes.last() == LF

    fun hasReplacementCharacter(): Boolean = text.contains(REPLACEMENT)

    fun separatorLineCount(): Int {
        var sectionEnd: String? = null
        var count = 0
        for (line in lines) {
            if (sectionEnd != null) {
                if (line == sectionEnd) sectionEnd = null
            } else {
                sectionEnd = sectionTerminator(line)
                if (line == SEPARATOR) count++
            }
        }
        return count
    }

    fun blocks(): List<ExportBlock> {
        val collected = mutableListOf<ExportBlock>()
        var index = 0
        while (index < lines.size) {
            if (lines[index] != SEPARATOR) {
                index++
                continue
            }
            val infoIndex = index + 1
            if (infoIndex >= lines.size || lines[infoIndex].isBlank() || lines[infoIndex] == SEPARATOR) {
                index++
                continue
            }
            val body = mutableListOf(lines[infoIndex])
            var cursor = infoIndex + 1
            var sectionEnd: String? = null
            while (cursor < lines.size) {
                val line = lines[cursor]
                if (sectionEnd == null && (line.isBlank() || line == SEPARATOR)) break
                if (sectionEnd == null) {
                    sectionEnd = sectionTerminator(line)
                } else if (line == sectionEnd) {
                    sectionEnd = null
                }
                body.add(line)
                cursor++
            }
            collected.add(
                ExportBlock(
                    infoLine = body.first(),
                    lines = body,
                )
            )
            index = cursor
        }
        return collected
    }

    fun linesStartingWith(prefix: String): List<String> = lines.filter { it.startsWith(prefix) }

    private fun sectionTerminator(line: String): String? =
        if (line.startsWith("--- ") && line.endsWith(" Start")) {
            "${line.removeSuffix(" Start")} End"
        } else {
            null
        }

    companion object {
        const val SEPARATOR = "----------------------------------------"
        const val REPLACEMENT = "�"

        private const val LF: Byte = 0x0A

        fun strictDecode(bytes: ByteArray): String {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }

        fun decodesStrictly(bytes: ByteArray): Boolean = runCatching { strictDecode(bytes) }.isSuccess
    }
}
