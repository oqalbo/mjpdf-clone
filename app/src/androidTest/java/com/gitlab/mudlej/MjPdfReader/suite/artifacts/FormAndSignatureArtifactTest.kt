// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BaselineCapture
import com.gitlab.mudlej.MjPdfReader.suite.core.PdfRenderOracle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class FormAndSignatureArtifactTest : ArtifactTestCase() {

    @Test
    @TestId("ART-11")
    fun aTypedFormFieldValueIsPresentInTheSavedFile() {
        val source = fixture(ArtifactFixtures.FORMS)
        val recordedRects = ProducedPdf.formFieldRectCount(source, 0)
        val sourcePages = ProducedPdf.pageCount(source)

        openReader(source).use {
            val field = FormFields.tapFirstTextField(reader)
            FormFields.typeAndConfirm(TYPED_VALUE)
            assertTrue(
                "typing into the field ${field.describe()} leaves an unsaved edit",
                reader.hasUnsavedEdits(),
            )

            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.FORMS))
            val saved = FormFields.readAll(produced, 0)

            assertTrue(
                "some field on page 1 of the saved file carries the typed value, it carries " +
                    saved.map { it.describe() },
                saved.any { it.text == TYPED_VALUE },
            )
            assertEquals(
                "the saved file declares the same number of field rectangles as the fixture",
                recordedRects,
                ProducedPdf.formFieldRectCount(produced, 0),
            )
            assertEquals(
                "the saved file keeps the fixture's page count",
                sourcePages,
                ProducedPdf.pageCount(produced),
            )
        }
    }

    @Test
    @TestId("ART-12")
    fun aToggledCheckboxStateIsPresentInTheSavedFile() {
        val source = fixture(ArtifactFixtures.FORMS)
        val originalUnchecked = FormFields.readAll(source, 0).filter { it.isCheckbox() && !it.checked }
        assertTrue("the fixture offers an unchecked checkbox to toggle", originalUnchecked.isNotEmpty())
        val sourcePageDigest = PdfRenderOracle.pageDigest(source)

        openReader(source).use {
            val toggled = FormFields.tapFirstCheckbox(reader)
            assertTrue("the live checkbox ${toggled.describe()} must be checked before saving", toggled.checked)
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.FORMS))

            val savedCheckboxes = FormFields.readAll(produced, 0).filter { it.isCheckbox() }
            val saved = savedCheckboxes.firstOrNull { it.name == toggled.name }
                ?: throw AssertionError(
                    "the saved file lost checkbox ${toggled.name}; it contains ${savedCheckboxes.map { it.describe() }}"
                )
            assertEquals("the saved checkbox must retain its live checked value", toggled.text, saved.text)
            assertNotEquals(
                "the saved checkbox value must differ from its untouched fixture value",
                originalUnchecked.first { it.name == toggled.name }.text,
                saved.text,
            )
            assertNotEquals(
                "the saved page rendering must visibly include the toggled checkbox appearance",
                sourcePageDigest,
                PdfRenderOracle.pageDigest(produced),
            )
            assertTrue(
                "the untouched fixture's checkbox still reads unchecked",
                FormFields.readAll(source, 0).any { it.isCheckbox() && !it.checked },
            )
        }
    }

    @Test
    @TestId("ART-13")
    fun aPlacedSignatureIsPresentInTheSavedFile() {
        SignatureFlow.seedAsPrimaryToolbarAction()
        val source = fixture(ArtifactFixtures.SMALL)
        val sourcePages = ProducedPdf.pageCount(source)
        val sourceSizes = ProducedPdf.pageSizes(source)
        val sourceTexts = ProducedPdf.pageTexts(source)
        val sourceFileDigest = ArtifactBytes.sha256(source)
        val sourcePageDigest = PdfRenderOracle.pageDigest(source)
        var sourceReaderDigest = ""

        openReader(source).use {
            sourceReaderDigest = PdfRenderOracle.digest(BaselineCapture.view(R.id.pdfView))
            SignatureFlow.drawAndPlace(reader)

            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))
            val producedPageDigest = PdfRenderOracle.pageDigest(produced)
            assertNotEquals(
                "rendering page 1 from the saved PDF must include the placed signature",
                sourcePageDigest,
                producedPageDigest,
            )
            assertEquals(
                "the produced file keeps the source page count",
                sourcePages,
                ProducedPdf.pageCount(produced),
            )
            assertEquals(
                "the produced file keeps every page size",
                sourceSizes,
                ProducedPdf.pageSizes(produced),
            )
            assertEquals(
                "the signature does not replace extracted page text",
                sourceTexts,
                ProducedPdf.pageTexts(produced),
            )
            assertNotEquals(
                "the saved PDF bytes must differ from the untouched source",
                sourceFileDigest,
                ArtifactBytes.sha256(produced),
            )
            leaveReader()

            openReader(produced).use {
                assertEquals(
                    "the reopened reader must be displaying the produced file",
                    Uri.fromFile(produced).toString(),
                    reader.state().documentUri,
                )
                assertNotEquals(
                    "the reopened reader page must visibly differ from the untouched source page",
                    sourceReaderDigest,
                    PdfRenderOracle.digest(BaselineCapture.view(R.id.pdfView)),
                )
                assertEquals(
                    "reparsing the file displayed by the reopened reader keeps the signed page rendering",
                    producedPageDigest,
                    PdfRenderOracle.pageDigest(produced),
                )
            }
        }
    }

    private companion object {
        const val TYPED_VALUE = "Mudlej Test 123"
    }
}

private fun ProducedFormField.describe(): String =
    "${type}:${name}=${text}${if (checked) " checked" else ""}"

private fun ProducedFormField.isCheckbox(): Boolean =
    type == com.shockwave.pdfium.PdfDocument.FormField.TYPE_CHECKBOX
