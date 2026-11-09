// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

class RootDescriptionFormatException(message: String) : AssertionError(message)

enum class LayoutDirection(val token: String) {
    LTR("ltr"),
    RTL("rtl");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

class RootDescription internal constructor(
    val raw: String,
    private val groups: Map<String, Map<String, String>>,
) {

    val groupNames: Set<String> get() = groups.keys

    fun group(name: String): Map<String, String>? = groups[name]

    fun value(group: String, key: String): String? = groups[group]?.get(key)?.takeIf { it.isNotEmpty() }

    fun int(group: String, key: String): Int? = value(group, key)?.let {
        it.toIntOrNull() ?: fail(group, key, it, "an integer")
    }
    fun long(group: String, key: String): Long? = value(group, key)?.let {
        it.toLongOrNull() ?: fail(group, key, it, "a long integer")
    }

    fun boolean(group: String, key: String): Boolean? = when (val raw = value(group, key)) {
        null -> null
        "true" -> true
        "false" -> false
        else -> fail(group, key, raw, "true or false")
    }

    val layoutDirection: LayoutDirection?
        get() = value(LAYOUT_GROUP, LAYOUT_KEY)?.let {
            LayoutDirection.ofToken(it) ?: fail(LAYOUT_GROUP, LAYOUT_KEY, it, "ltr or rtl")
        }

    private fun fail(group: String, key: String, value: String, expected: String): Nothing =
        throw RootDescriptionFormatException(
            "Group $group key $key holds \"$value\" which is not $expected, in root description: $raw"
        )

    override fun toString(): String = raw

    companion object {
        const val LAYOUT_GROUP = "dir"
        const val LAYOUT_KEY = "layout"
    }
}

object RootDescriptionParser {

    const val PAIR_SEPARATOR = ";"
    const val KEY_VALUE_SEPARATOR = "="
    const val GROUP_SEPARATOR = ":"

    fun parse(raw: String): RootDescription {
        val groups = LinkedHashMap<String, MutableMap<String, String>>()
        if (raw.isEmpty()) return RootDescription(raw, groups)

        var currentGroup: String? = null
        raw.split(PAIR_SEPARATOR).forEach { token ->
            if (token.isEmpty()) {
                throw RootDescriptionFormatException(
                    "Root description holds an empty pair, which means a stray or trailing " +
                        "\"$PAIR_SEPARATOR\": $raw"
                )
            }
            val equals = token.indexOf(KEY_VALUE_SEPARATOR)
            if (equals <= 0) {
                throw RootDescriptionFormatException("Root description token \"$token\" is not key=value: $raw")
            }
            var key = token.substring(0, equals)
            val value = token.substring(equals + 1)

            val groupBoundary = key.indexOf(GROUP_SEPARATOR)
            if (groupBoundary >= 0) {
                val groupName = key.substring(0, groupBoundary)
                if (groupName.isEmpty()) {
                    throw RootDescriptionFormatException("Root description token \"$token\" has an empty group: $raw")
                }
                currentGroup = groupName
                key = key.substring(groupBoundary + 1)
            }

            val group = currentGroup
                ?: throw RootDescriptionFormatException(
                    "Root description token \"$token\" appears before any group is opened: $raw"
                )
            if (key.isEmpty()) {
                throw RootDescriptionFormatException("Root description token \"$token\" has an empty key: $raw")
            }
            groups.getOrPut(group) { LinkedHashMap() }[key] = value
        }
        return RootDescription(raw, groups)
    }
}
