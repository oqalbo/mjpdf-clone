// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.app.Activity
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.model.LinkTapEvent
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentities
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File
import java.time.LocalDateTime

object BookmarksReader {

    fun viewIntent(name: String): Intent = ReaderRobot.viewIntent(BookmarksFixtures.uri(name))

    fun jumpToPage(reader: ReaderRobot, pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed while jumping to page index $pageIndex")
            val pdfView = activity.findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while jumping to page index $pageIndex")
            pdfView.jumpTo(pageIndex, false)
        }
        reader.awaitPageIndex(pageIndex, timeoutMs)
        return reader.awaitMotionSettled(timeoutMs)
    }

    private fun pagePointAtFraction(
        reader: ReaderRobot,
        xFraction: Float,
        yFraction: Float,
    ): Point {
        val pageRect = reader.readState().pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle")
        return Point(
            (pageRect.left + pageRect.width() * xFraction).toInt(),
            (pageRect.top + pageRect.height() * yFraction).toInt(),
        )
    }

    fun findLinkAtPageFraction(
        reader: ReaderRobot,
        xFraction: Float,
        yFraction: Float,
    ): LinkTapEvent? {
        val point = pagePointAtFraction(reader, xFraction, yFraction)
        return TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed while finding a link")
            val pdfView = activity.findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while finding a link")
            pdfView.findLinkAt(point.x.toFloat(), point.y.toFloat())
        }
    }

    fun openAction(@StringRes labelRes: Int) {
        val dialog = ActionsDialogRobot()
        dialog.open()
        dialog.awaitTile(string(labelRes))
        dialog.tap(string(labelRes))
    }

    fun addBookmarkLabel(): String = string(R.string.add_bookmark)

    fun removeBookmarkLabel(): String = string(R.string.remove_bookmark)

    fun toggleBookmark() {
        val dialog = ActionsDialogRobot()
        dialog.open()
        val label = bookmarkTileLabel(dialog)
            ?: throw ConditionTimeoutException(
                "The actions dialog offers neither \"${addBookmarkLabel()}\" nor \"${removeBookmarkLabel()}\", " +
                    "so the bookmark action is not reachable. Its tiles are ${dialog.tileLabels()}"
            )
        dialog.tap(label)
    }

    fun bookmarkActionLabel(): String? {
        val dialog = ActionsDialogRobot()
        dialog.open()
        val label = bookmarkTileLabel(dialog)
        dialog.dismiss()
        return label
    }

    fun awaitBookmarkActionLabel(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the reader bookmark action reads \"$expected\"",
            timeoutMs = timeoutMs,
            read = { bookmarkActionLabel() },
        ) { it == expected }
    }

    private fun bookmarkTileLabel(dialog: ActionsDialogRobot): String? {
        Await.until("the actions dialog offers the bookmark tile") {
            dialog.hasTile(addBookmarkLabel()) || dialog.hasTile(removeBookmarkLabel())
        }
        return when {
            dialog.hasTile(removeBookmarkLabel()) -> removeBookmarkLabel()
            dialog.hasTile(addBookmarkLabel()) -> addBookmarkLabel()
            else -> null
        }
    }

    fun string(@StringRes resId: Int): String = TestEnvironment.onMainThread {
        val context = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: TestEnvironment.targetContext
        context.getString(resId)
    }
}

object BookmarksIdentities {

    fun of(name: String): DocumentIdentities = DocumentIdentity.of(BookmarksFixtures.file(name))
        ?: throw AssertionError("No document identity could be computed for the fixture $name")

    fun of(file: File): DocumentIdentities = DocumentIdentity.of(file)
        ?: throw AssertionError("No document identity could be computed for ${file.absolutePath}")

    fun identityOf(name: String): String = of(name).identity
}

object Bookmarks {

    fun seed(fileHash: String, pageIndex: Int, label: String? = null, sortOrder: Int = 0) {
        seedAll(listOf(UserBookmark(fileHash, pageIndex, label, LocalDateTime.now(), sortOrder)))
    }

    fun seedAll(bookmarks: List<UserBookmark>) {
        Records.database.userBookmarkDao().upsertAll(bookmarks)
    }

    fun seedRecord(
        hash: String,
        uri: Uri,
        pageNumber: Int = 0,
        fileName: String = "seeded.pdf",
    ) {
        Records.database.pdfRecordDao().insert(
            PdfRecord(
                hash,
                pageNumber,
                uri,
                0,
                fileName,
                null,
                LocalDateTime.now(),
                ReadingStatus.UNSET,
                false,
            )
        )
    }

    fun all(): List<UserBookmark> = Records.database.userBookmarkDao().findAll()

    fun of(fileHash: String): List<UserBookmark> = Records.bookmarksOf(fileHash)

    fun row(fileHash: String, pageIndex: Int): UserBookmark? =
        of(fileHash).firstOrNull { it.pageIndex == pageIndex }

    fun pageIndexes(fileHash: String): List<Int> = of(fileHash).map { it.pageIndex }

    fun awaitRow(
        fileHash: String,
        pageIndex: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): UserBookmark = Await.untilNotNull(
        "a UserBookmark row for page index $pageIndex of $fileHash exists",
        timeoutMs,
    ) { row(fileHash, pageIndex) }

    fun awaitCount(
        fileHash: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<UserBookmark> = Await.untilValue(
        condition = "the document $fileHash holds $expected bookmark rows",
        timeoutMs = timeoutMs,
        read = { of(fileHash) },
    ) { it.size == expected }

    fun awaitTotalCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<UserBookmark> =
        Await.untilValue(
            condition = "the UserBookmark table holds $expected rows in total",
            timeoutMs = timeoutMs,
            read = { all() },
        ) { it.size == expected }

    fun awaitEmpty(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitTotalCount(0, timeoutMs)
    }

    fun awaitStableCount(
        fileHash: String,
        windowMs: Long = STABLE_WINDOW_MS,
    ): List<UserBookmark> {
        var last = of(fileHash).size
        Await.untilTrueContinuously(
            condition = "the bookmark row count of $fileHash stops changing",
            stableForMs = windowMs,
            timeoutMs = Await.LONG_TIMEOUT_MS,
        ) {
            val current = of(fileHash).size
            val stable = current == last
            last = current
            stable
        }
        return of(fileHash)
    }

    fun assertCountStays(
        fileHash: String,
        expected: Int,
        windowMs: Long = STABLE_WINDOW_MS,
    ) {
        Await.assertThroughout(
            condition = "the document $fileHash keeps exactly $expected bookmark rows",
            windowMs = windowMs,
        ) { of(fileHash).size == expected }
    }

    const val STABLE_WINDOW_MS = 1_000L
}

object BookmarksRows {

    fun recycler(activity: Activity, @IdRes viewId: Int): RecyclerView =
        activity.findViewById(viewId)
            ?: throw ConditionTimeoutException("The screen has no list ${resourceName(viewId)}")

    fun itemCount(activity: Activity, @IdRes viewId: Int): Int = TestEnvironment.onMainThread {
        recycler(activity, viewId).adapter?.itemCount ?: 0
    }

    fun itemView(activity: Activity, @IdRes viewId: Int, position: Int): View? =
        TestEnvironment.onMainThread {
            recycler(activity, viewId).findViewHolderForAdapterPosition(position)?.itemView
        }

    fun scrollTo(activity: Activity, @IdRes viewId: Int, position: Int) {
        TestEnvironment.onMainThread { recycler(activity, viewId).scrollToPosition(position) }
        TestEnvironment.waitForIdleSync()
    }

    fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())
}
