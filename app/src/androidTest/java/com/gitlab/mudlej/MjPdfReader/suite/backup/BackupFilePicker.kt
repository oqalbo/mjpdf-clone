// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot

object BackupFilePicker {
    fun choose(
        picker: SafPickerRobot,
        fileName: String,
        onSelectionDispatched: () -> Unit = {},
    ) {
        picker.awaitShown()
        picker.navigateToDownloads()
        picker.chooseFileNamed(fileName, onSelectionDispatched = onSelectionDispatched)
    }
}
