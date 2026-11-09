// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection.LEFT_TO_RIGHT
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection.RIGHT_TO_LEFT
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingDirectionTest {

    @Test
    fun explicitReadingDirectionOverridesDetection() {
        assertEquals(LEFT_TO_RIGHT, ReadingDirection.effective(LEFT_TO_RIGHT, RIGHT_TO_LEFT))
        assertEquals(RIGHT_TO_LEFT, ReadingDirection.effective(RIGHT_TO_LEFT, LEFT_TO_RIGHT))
    }

    @Test
    fun detectedReadingDirectionIsUsedWithoutAnOverride() {
        assertEquals(RIGHT_TO_LEFT, ReadingDirection.effective(null, RIGHT_TO_LEFT))
    }

    @Test
    fun unknownDetectionFallsBackToLeftToRight() {
        assertEquals(LEFT_TO_RIGHT, ReadingDirection.effective(null, UNKNOWN))
    }

    @Test
    fun missingOverrideAndDetectionFallBackToLeftToRight() {
        assertEquals(LEFT_TO_RIGHT, ReadingDirection.effective(null, null))
    }

    @Test
    fun unknownIsADetectedValueButCannotBeAnOverride() {
        assertEquals(UNKNOWN, ReadingDirection.fromId("unknown"))
        assertNull(ReadingDirection.fromOverrideId("unknown"))
    }

    @Test
    fun missingAndUnrecognizedIdsHaveNoReadingDirection() {
        assertNull(ReadingDirection.fromId(null))
        assertNull(ReadingDirection.fromId("sideways"))
    }

    @Test
    fun everyReadingDirectionIdIdentifiesExactlyOneValue() {
        val directions = ReadingDirection.values()
        assertEquals(directions.size, directions.map { it.id }.toSet().size)
        for (direction in directions) {
            assertEquals(direction, ReadingDirection.fromId(direction.id))
        }
    }
}
