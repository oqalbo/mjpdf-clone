// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

object SearchSessionCache {
    private const val MAX_SESSIONS = 5
    private const val MAX_CACHED_HITS = 5000
    private const val NO_POSITION = -1

    data class Key(val fileHash: String, val query: String, val ignoreAccents: Boolean)

    data class Hit(
        val pageNumber: Int,
        val originalIndex: Int,
        val resultIndex: Int,
        val expanded: Boolean = false,
        val text: String = "",
        val inputStart: Int = 0,
        val inputEnd: Int = 0,
    ) {
        val matchLength: Int
            get() = inputEnd - inputStart
    }

    data class Session(
        val key: Key,
        val hits: List<Hit>,
        val listPosition: Int = NO_POSITION,
        val listOffsetPx: Int = 0,
        val nestedQuery: String? = null,
    )

    private val sessions = object : LinkedHashMap<Key, Session>(MAX_SESSIONS + 1, 0.75F, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Session>?): Boolean {
            return size > MAX_SESSIONS
        }
    }

    @Synchronized
    fun get(fileHash: String?, query: String, ignoreAccents: Boolean): Session? {
        val key = key(fileHash, query, ignoreAccents) ?: return null
        return sessions[key]
    }

    @Synchronized
    fun put(fileHash: String?, query: String, ignoreAccents: Boolean, hits: List<Hit>) {
        val key = key(fileHash, query, ignoreAccents) ?: return
        if (hits.size > MAX_CACHED_HITS) {
            sessions.remove(key)
            return
        }
        sessions[key] = Session(key, hits.toList())
    }

    @Synchronized
    fun updateUiState(
        fileHash: String?,
        query: String,
        ignoreAccents: Boolean,
        listPosition: Int,
        listOffsetPx: Int,
        nestedQuery: String?,
    ) {
        val key = key(fileHash, query, ignoreAccents) ?: return
        val session = sessions[key] ?: return
        sessions[key] = session.copy(
            listPosition = listPosition,
            listOffsetPx = listOffsetPx,
            nestedQuery = nestedQuery?.takeIf { it.isNotBlank() },
        )
    }

    @Synchronized
    fun updateHit(
        fileHash: String?,
        query: String,
        ignoreAccents: Boolean,
        resultIndex: Int,
        expanded: Boolean,
        text: String,
        inputStart: Int,
        inputEnd: Int,
    ) {
        val key = key(fileHash, query, ignoreAccents) ?: return
        val session = sessions[key] ?: return
        sessions[key] = session.copy(
            hits = session.hits.map { hit ->
                if (hit.resultIndex == resultIndex) {
                    hit.copy(expanded = expanded, text = text, inputStart = inputStart, inputEnd = inputEnd)
                } else {
                    hit
                }
            },
        )
    }

    private fun key(fileHash: String?, query: String, ignoreAccents: Boolean): Key? {
        val hash = fileHash?.takeIf { it.isNotBlank() } ?: return null
        val trimmedQuery = query.trim().takeIf { it.isNotBlank() } ?: return null
        return Key(hash, trimmedQuery, ignoreAccents)
    }
}
