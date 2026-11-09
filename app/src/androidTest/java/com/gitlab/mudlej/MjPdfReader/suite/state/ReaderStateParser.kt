// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateFields as Field

object ReaderStateParser {

    fun parse(raw: String): ReaderState {
        val values = LinkedHashMap<String, String>()
        if (raw.isEmpty()) return ReaderState(raw, values)
        raw.split(Field.PAIR_SEPARATOR).forEachIndexed { index, pair ->
            if (pair.isEmpty()) {
                throw ReaderStateFormatException(
                    "Reader state holds an empty pair at position $index, which means a stray or trailing " +
                        "\"${Field.PAIR_SEPARATOR}\": $raw"
                )
            }
            val separator = pair.indexOf(Field.KEY_VALUE_SEPARATOR)
            if (separator <= 0) {
                throw ReaderStateFormatException("Reader state pair \"$pair\" is not key=value: $raw")
            }
            val key = pair.substring(0, separator)
            val value = pair.substring(separator + 1)
            if (value.contains(Field.KEY_VALUE_SEPARATOR)) {
                throw ReaderStateFormatException("Reader state value for $key contains \"=\": $raw")
            }
            if (values.containsKey(key)) {
                throw ReaderStateFormatException("Reader state repeats the key $key: $raw")
            }
            values[key] = unescape(value)
        }
        return ReaderState(raw, values)
    }

    fun unescape(value: String): String {
        if (!value.contains('%')) return value
        return value
            .replace(Field.ESCAPED_PAIR_SEPARATOR, Field.PAIR_SEPARATOR)
            .replace(Field.ESCAPED_KEY_VALUE_SEPARATOR, Field.KEY_VALUE_SEPARATOR)
            .replace(Field.ESCAPED_PERCENT, "%")
    }

    fun escape(value: String): String = value
        .replace("%", Field.ESCAPED_PERCENT)
        .replace(Field.PAIR_SEPARATOR, Field.ESCAPED_PAIR_SEPARATOR)
        .replace(Field.KEY_VALUE_SEPARATOR, Field.ESCAPED_KEY_VALUE_SEPARATOR)

    fun grammarViolations(raw: String): List<String> {
        val violations = mutableListOf<String>()
        val state = try {
            parse(raw)
        } catch (error: ReaderStateFormatException) {
            return listOf(error.message ?: "reader state could not be parsed")
        }

        state.unknownKeys.forEach {
            violations += "unknown key $it is not in the field table"
        }

        Field.alwaysPresent.forEach { key ->
            if (key !in state.presentKeys) violations += "always present key $key is missing"
        }

        val selectionKeysPresent = Field.selection.filter { it in state.presentKeys }
        if (selectionKeysPresent.isNotEmpty() && selectionKeysPresent.size != Field.selection.size) {
            violations += "the selection group is partially present, holding only $selectionKeysPresent"
        }

        val expectedOrder = Field.ordered.filter { it in state.presentKeys }
        val actualOrder = state.presentKeys.filter { it in Field.known }
        if (expectedOrder != actualOrder) {
            violations += "keys are out of table order, expected $expectedOrder but read $actualOrder"
        }

        state.presentKeys.forEach { key ->
            val value = state.rawValue(key).orEmpty()
            if (value.equals("null", ignoreCase = true)) {
                violations += "key $key holds the literal word null instead of an empty value"
            }
        }

        violations += escapingViolations(raw)

        return violations
    }

    private fun escapingViolations(raw: String): List<String> {
        val violations = mutableListOf<String>()
        raw.split(Field.PAIR_SEPARATOR).forEach { pair ->
            val separator = pair.indexOf(Field.KEY_VALUE_SEPARATOR)
            if (separator <= 0) return@forEach
            val key = pair.substring(0, separator)
            val wireValue = pair.substring(separator + 1)
            var index = wireValue.indexOf('%')
            while (index >= 0) {
                val escape = wireValue.substring(index, minOf(index + 3, wireValue.length))
                if (escape != Field.ESCAPED_PERCENT &&
                    escape != Field.ESCAPED_PAIR_SEPARATOR &&
                    escape != Field.ESCAPED_KEY_VALUE_SEPARATOR
                ) {
                    violations += "key $key holds an unescaped percent at index $index of its value"
                    return@forEach
                }
                index = wireValue.indexOf('%', index + 3)
            }
        }
        return violations
    }
}
