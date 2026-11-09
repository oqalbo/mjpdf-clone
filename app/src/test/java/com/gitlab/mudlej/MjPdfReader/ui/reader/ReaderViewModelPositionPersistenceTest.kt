// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewModelPositionPersistenceTest {

    @Test
    fun leavingTheSuppressedPageEnablesPersistenceEvenAfterReturning() {
        val viewModel = ReaderViewModel(SavedStateHandle())
        viewModel.suppressPositionPersistAt(0)

        repeat(3) {
            assertFalse(viewModel.canPersistPosition(0))
        }
        assertTrue(viewModel.canPersistPosition(1))
        assertTrue(viewModel.canPersistPosition(0))
        assertTrue(viewModel.canPersistPosition(2))
        assertTrue(viewModel.canPersistPosition(0))
    }

    @Test
    fun clearingSuppressionEnablesPersistenceWithoutLeavingThePage() {
        val viewModel = ReaderViewModel(SavedStateHandle())
        viewModel.suppressPositionPersistAt(7)
        assertFalse(viewModel.canPersistPosition(7))

        viewModel.clearPositionPersistSuppression()

        assertTrue(viewModel.canPersistPosition(7))
        assertTrue(viewModel.canPersistPosition(7))
    }
}
