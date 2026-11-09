// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ExportBlock
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ExportFile
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ExportFlow
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import java.io.File

class ExportedFile(val name: String, private val parsed: ExportFile) {

    val text: String get() = parsed.text

    val lines: List<String> get() = parsed.lines

    val headerLines: List<String>
        get() = parsed.lines.takeWhile { it != ExportFile.SEPARATOR }.dropLastWhile { it.isBlank() }

    fun blocks(): List<ExportBlock> = parsed.blocks()

    fun separatorLineCount(): Int = parsed.separatorLineCount()

    fun linesStartingWith(prefix: String): List<String> = parsed.linesStartingWith(prefix)

    fun decodesStrictly(): Boolean = ExportFile.decodesStrictly(parsed.bytes)

    fun hasReplacementCharacter(): Boolean = parsed.hasReplacementCharacter()

    fun hasByteOrderMark(): Boolean = parsed.hasByteOrderMark()

    override fun toString(): String = "$name:\n$text"
}

object ListExport {

    const val SEPARATOR = ExportFile.SEPARATOR
    const val OPEN_QUOTE = "“"
    const val CLOSE_QUOTE = "”"
    val downloads: File = File(Fixtures.externalRoot, "Download")

    fun quoteStart(): String = "--- ${string(R.string.export_quote_label)} Start"

    fun quoteEnd(): String = "--- ${string(R.string.export_quote_label)} End"

    fun noteStart(): String = "--- ${string(R.string.note)} Start"

    fun highlightsFileName(documentTitle: String): String =
        "$documentTitle - ${string(R.string.user_highlights_title)}.txt"

    fun notesFileName(documentTitle: String): String =
        "$documentTitle - ${string(R.string.user_notes_title)}.txt"

    fun target(name: String): File = File(downloads, name)

    fun variantsOf(name: String): List<File> = ExportFlow.variantsOf(downloads, name)

    fun deleteAllVariants(name: String) {
        ExportFlow.deleteAllVariants(downloads, name, tolerateFailures = false)
    }

    fun requireNoVariantBeside(name: String) {
        val extra = variantsOf(name).filter { it.name != name }
        if (extra.isNotEmpty()) {
            throw AssertionError(
                "The picker renamed this run's export, so ${extra.map { it.name }} sit beside $name and the " +
                    "bytes read below would belong to an earlier run rather than to this one"
            )
        }
    }

    fun awaitWritten(name: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ExportedFile {
        val file = target(name)
        Await.until("the export $name appears in ${downloads.absolutePath}", timeoutMs) {
            file.isFile && file.length() > 0
        }
        return read(name)
    }

    fun read(name: String): ExportedFile {
        val file = target(name)
        if (!file.isFile) {
            throw AssertionError("No export named $name exists in ${downloads.absolutePath}")
        }
        return ExportedFile(name, ExportFile(file))
    }

    fun infoLines(exported: ExportedFile): List<String> = exported.blocks().map { it.infoLine }

    fun noteLines(exported: ExportedFile): List<String> = exported.lines.filter { it == noteStart() }

    fun quoteLines(exported: ExportedFile): List<String> = exported.lines.filter { it == quoteStart() }

    fun exists(name: String): Boolean = target(name).isFile

    fun exportedDoneMessage(): String = string(R.string.export_done)

    private fun string(resId: Int): String = TestEnvironment.appString(resId)
}

object ExportPicker {

    const val FILE_NAME_FIELD = "android:id/title"

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        SafPickerRobot().awaitShown(timeoutMs)
    }

    fun isShowing(): Boolean = SafPickerRobot().isShowing()

    fun prefilledName(): String {
        awaitShown()
        return nameField()?.text
            ?: throw ConditionTimeoutException(
                "The create document picker shows no file name field, so its prefilled name cannot be read"
            )
    }

    fun saveIntoDownloadsKeepingName(): String {
        awaitShown()
        SafPickerRobot().navigateToDownloads()
        val name = prefilledName()
        tapSave()
        return name
    }

    fun cancel() {
        SafPickerRobot().cancel()
    }

    private fun tapSave() {
        val save = SafPickerRobot.SAVE_LABELS.firstNotNullOfOrNull { label ->
            TestEnvironment.uiDevice.wait(Until.findObject(By.text(label)), Await.SHORT_TIMEOUT_MS)
        } ?: throw ConditionTimeoutException(
            "The create document picker shows no save button, tried ${SafPickerRobot.SAVE_LABELS}"
        )
        save.click()
        SafPickerRobot().awaitPickerDismissed()
    }

    private fun nameField() =
        TestEnvironment.uiDevice.wait(
            Until.findObject(By.res(FILE_NAME_FIELD).clazz(NAME_FIELD_CLASS)),
            Await.SHORT_TIMEOUT_MS,
        ) ?: TestEnvironment.uiDevice.wait(
            Until.findObject(By.clazz(NAME_FIELD_CLASS)),
            Await.SHORT_TIMEOUT_MS,
        )

    private const val NAME_FIELD_CLASS = "android.widget.EditText"
}
