// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object PersistedGrantKeeper {

    private const val CAP_PRE_R = 128
    private const val CAP_R_AND_UP = 512

    fun takeReadGrant(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") {
            return false
        }
        val resolver = context.contentResolver
        runCatching {
            val cap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CAP_R_AND_UP else CAP_PRE_R
            val persisted = resolver.persistedUriPermissions
            val grants = persisted.map { GrantInfo(it.uri.toString(), it.persistedTime, it.isWritePermission) }
            grantsToRelease(grants, cap).forEach { grant ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(grant.uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
    }
}

internal data class GrantInfo(val uri: String, val time: Long, val write: Boolean)

internal fun grantsToRelease(grants: List<GrantInfo>, cap: Int): List<GrantInfo> {
    val excess = grants.size - (cap - CAP_HEADROOM)
    return if (excess >= 0) grants.filter { !it.write }.sortedBy { it.time }.take(excess + 1) else emptyList()
}

private const val CAP_HEADROOM = 8
