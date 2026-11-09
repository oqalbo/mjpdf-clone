// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.net.Uri
import java.io.File

enum class Availability {
    AVAILABLE,
    ACCESS_UNKNOWN,
    MISSING,
}

class AvailabilityProbe(
    private val context: Context,
    private val hasFullAccess: Boolean,
) {

    private val persistedReadGrants: Set<String> by lazy {
        runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun availabilityOf(uri: Uri): Availability {
        return when (uri.scheme) {
            "file" -> {
                val readable = uri.path?.let { File(it).canRead() } == true
                fileAvailability(readable, hasFullAccess)
            }
            "content" -> contentAvailability(uri)
            else -> Availability.AVAILABLE
        }
    }

    private fun contentAvailability(uri: Uri): Availability {
        val opens = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
        if (opens) {
            return Availability.AVAILABLE
        }

        val hasPersistedReadGrant = uri.toString() in persistedReadGrants
        return contentAvailability(opens, hasPersistedReadGrant, uri.authority == MEDIA_AUTHORITY, hasFullAccess)
    }

    private companion object {
        const val MEDIA_AUTHORITY = "media"
    }
}

internal fun fileAvailability(readable: Boolean, hasFullAccess: Boolean): Availability = when {
    readable -> Availability.AVAILABLE
    !hasFullAccess -> Availability.ACCESS_UNKNOWN
    else -> Availability.MISSING
}

internal fun contentAvailability(
    opens: Boolean,
    hasPersistedGrant: Boolean,
    isMediaAuthority: Boolean,
    hasFullAccess: Boolean,
): Availability = when {
    opens -> Availability.AVAILABLE
    hasPersistedGrant -> Availability.MISSING
    isMediaAuthority && !hasFullAccess -> Availability.ACCESS_UNKNOWN
    else -> Availability.MISSING
}
