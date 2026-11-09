// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

enum class ReadingDirection(val id: String) {
    LEFT_TO_RIGHT("ltr"),
    RIGHT_TO_LEFT("rtl"),
    UNKNOWN("unknown");

    val isRightToLeft: Boolean
        get() = this == RIGHT_TO_LEFT

    companion object {
        fun fromId(id: String?): ReadingDirection? {
            return values().firstOrNull { it.id == id }
        }

        fun fromOverrideId(id: String?): ReadingDirection? {
            return fromId(id)?.takeUnless { it == UNKNOWN }
        }

        fun effective(
            overrideDirection: ReadingDirection?,
            detectedDirection: ReadingDirection?,
        ): ReadingDirection {
            return overrideDirection
                ?: detectedDirection?.takeUnless { it == UNKNOWN }
                ?: LEFT_TO_RIGHT
        }
    }
}
