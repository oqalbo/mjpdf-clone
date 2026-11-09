// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

enum class DocumentLoadState(val token: String) {
    IDLE("Idle"),
    LOADING("Loading"),
    PASSWORD_REQUIRED("PasswordRequired"),
    LOADED("Loaded"),
    FAILED("Failed");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

enum class ViewerState(val token: String) {
    DEFAULT("DEFAULT"),
    LOADED("LOADED"),
    SHOWN("SHOWN"),
    ERROR("ERROR");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

enum class RenderActivity(val token: String) {
    IDLE("idle"),
    BUSY("busy");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

enum class RenderQuality(val token: String) {
    FULL("full"),
    SNAPSHOT("snapshot"),
    PREVIEW("preview"),
    NONE("none");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

enum class PageFitPolicy(val token: String) {
    WIDTH("WIDTH"),
    HEIGHT("HEIGHT"),
    BOTH("BOTH");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}

enum class SelectionDrag(val token: String) {
    NONE("none"),
    START("start"),
    END("end");

    companion object {
        fun ofToken(token: String) = entries.firstOrNull { it.token == token }
    }
}
