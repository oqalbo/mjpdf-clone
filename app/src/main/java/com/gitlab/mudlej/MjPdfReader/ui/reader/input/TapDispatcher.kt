// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import android.view.MotionEvent

class TapDispatcher(private val handlers: List<(MotionEvent) -> Boolean>) {

    fun dispatch(event: MotionEvent): Boolean {
        for (handler in handlers) {
            if (handler(event)) {
                return true
            }
        }
        return false
    }
}
