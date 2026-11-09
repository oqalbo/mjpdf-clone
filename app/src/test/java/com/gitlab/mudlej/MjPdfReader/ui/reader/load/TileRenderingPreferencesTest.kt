// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import org.junit.Assert.assertEquals
import org.junit.Test

class TileRenderingPreferencesTest {
    @Test
    fun smallTilesClampTheCacheAtItsMaximum() = assertPreferences(128f, 480)

    @Test
    fun defaultTilesUseThePixelBudget() = assertPreferences(256f, 240)

    @Test
    fun doublingTileSizeQuartersTheCache() = assertPreferences(512f, 60)

    @Test
    fun largeTilesClampTheCacheAtItsMinimum() = assertPreferences(1024f, 24)

    private fun assertPreferences(partSize: Float, expectedCacheSize: Int) {
        val preferences = tileRenderingPreferences(partSize)
        assertEquals(partSize, preferences.partSize, 0f)
        assertEquals(expectedCacheSize, preferences.cacheSize)
    }
}
