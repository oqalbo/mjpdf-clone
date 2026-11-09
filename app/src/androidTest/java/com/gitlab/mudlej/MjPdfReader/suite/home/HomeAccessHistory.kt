// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.ui.home.Availability
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals

internal fun platformAccessGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    legacyPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE) &&
        legacyPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun legacyPermissionGranted(permission: String): Boolean =
    TestEnvironment.targetContext.checkPermission(permission, Process.myPid(), Process.myUid()) ==
        PackageManager.PERMISSION_GRANTED

internal fun HomeRobot.launchRecent(expectedStorageAccess: Boolean): HomePage {
    launchFromLauncher()
    awaitState("Home commits a render with storage access $expectedStorageAccess") {
        it.hasHomeGroup && it.storageAccess == expectedStorageAccess && (it.generation ?: 0) >= 1
    }
    return selectTab(HomeTab.RECENT)
}

internal fun HomePage.awaitItemWithHash(hash: String, availability: Availability): HomeItem =
    Await.untilValue(
        "the $tab tab publishes the row with hash $hash as $availability",
        read = { items().firstOrNull { it.hash == hash && it.availability == availability } },
    ) { it != null }!!

internal fun assertRecentPermissionCard(recent: HomePage) {
    val cards = recent.sections().filterIsInstance<HomeSection.PermissionCard>()
    assertEquals("one coherent Recent permission card is published", 1, cards.size)
    assertEquals(R.string.home_recent_permission_message, cards.single().messageRes)
}

internal fun assertHistoryMatchesRecord(item: HomeItem, record: PdfRecord) {
    assertEquals(record.hash, item.hash)
    assertEquals(record.uri, item.uri)
    assertEquals(record.pageNumber, item.pageNumber)
    assertEquals(record.length, item.length)
    assertEquals(record.lastOpened, item.lastOpened)
    assertEquals(record.hash, item.coverKey)
}

internal fun historyFingerprint(item: HomeItem): String = listOf(
    item.hash,
    item.uri.toString(),
    item.title,
    item.pageNumber.toString(),
    item.length.toString(),
    item.lastOpened.toString(),
    item.coverKey,
).joinToString("\n")
