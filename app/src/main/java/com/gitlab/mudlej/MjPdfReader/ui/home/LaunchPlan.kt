// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewRelease
import com.gitlab.mudlej.MjPdfReader.ui.about.whatsNewReleaseForUpgrade

internal object LaunchPlan {
    sealed interface PreferenceWrite {
        data class FirstInstall(val value: Boolean) : PreferenceWrite
        data class ShowFeaturesDialog(val value: Boolean) : PreferenceWrite
        data class LastSeenVersionCode(val value: Int) : PreferenceWrite
    }

    data class Decision(
        val launchIntro: Boolean,
        val release: WhatsNewRelease?,
        val writes: List<PreferenceWrite>,
    )

    fun decide(
        firstInstall: Boolean,
        homeDisabled: Boolean,
        showFeaturesDialog: Boolean,
        lastSeenVersionCode: Int,
        currentVersionCode: Int,
    ): Decision {
        if (firstInstall) return Decision(
            launchIntro = true,
            release = null,
            writes = listOf(
                PreferenceWrite.FirstInstall(false),
                PreferenceWrite.ShowFeaturesDialog(true),
                PreferenceWrite.LastSeenVersionCode(currentVersionCode),
            ),
        )
        val upgrade = whatsNewReleaseForUpgrade(lastSeenVersionCode, currentVersionCode)
        val release = upgrade ?: if (!homeDisabled && showFeaturesDialog) WhatsNewRelease.CURRENT else null
        val writes = buildList {
            if (upgrade != null) add(PreferenceWrite.LastSeenVersionCode(currentVersionCode))
            if (!homeDisabled && release != null) add(PreferenceWrite.ShowFeaturesDialog(false))
        }
        return Decision(false, release, writes)
    }
}
