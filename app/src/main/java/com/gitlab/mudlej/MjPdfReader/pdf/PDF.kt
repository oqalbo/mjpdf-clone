// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

object PDF {

    // constants
    const val FILE_TYPE = "application/pdf"
    const val HASH_SIZE = 1024 * 1024
    const val TABLE_OF_CONTENTS_TEXT_SIZE = 24F
    const val TABLE_OF_CONTENTS_TEXT_SIZE_DEC = 2F
    const val TABLE_OF_CONTENTS_RESULT_OK = 48645
    const val SEARCH_RESULT_OK = 48632
    const val LINK_RESULT_OK = 48032
    const val GO_TO_PAGE_RESULT_OK = 48214
    const val SCREENSHOT_IMAGE_QUALITY = 100
    const val SEARCH_RESULT_OFFSET = 40
    const val ADDITIONAL_SEARCH_RESULT_OFFSET = 100
    const val TOO_MANY_RESULTS = 3500
    const val RESET_NUMBER = -1
    const val MIN_SEARCH_QUERY = 3

    // keys
    const val nameKey = "name"
    const val passwordKey = "password"
    const val pageNumberKey = "pageNumber"
    const val lengthKey = "length"
    const val uriKey = "uri"
    const val viewStateSavedKey = "viewStateSaved"
    const val viewStateZoomKey = "viewStateZoom"
    const val viewStatePageIndexKey = "viewStatePageIndex"
    const val viewStateSwipeVerticalKey = "viewStateSwipeVertical"
    const val viewStateHorizontalReadingDirectionRtlKey = "viewStateHorizontalReadingDirectionRtl"
    const val viewStateRelativeCrossAxisCenterKey = "viewStateRelativeCrossAxisCenter"
    const val viewStatePageCenterOffsetRatioKey = "viewStatePageCenterOffsetRatio"
    const val viewStatePagesPerRowKey = "viewStatePagesPerRow"
    const val viewStateFirstPageAloneKey = "viewStateFirstPageAlone"
    const val isFullScreenToggledKey = "isFullScreenToggled"
    const val autoScrollSpeedKey = "autoScrollSpeedKey"
    const val cropMarginsEnabledKey = "cropMarginsEnabled"
    const val readingDirectionOverrideKey = "readingDirectionOverride"
    const val detectedReadingDirectionKey = "detectedReadingDirection"
    const val effectiveReadingDirectionKey = "effectiveReadingDirection"
    const val hasUnsavedAnnotationsKey = "hasUnsavedAnnotations"
    const val sessionOwnedAnnotationKeysKey = "sessionOwnedAnnotationKeys"
    const val chosenTableOfContentsEntryKey = "chosenTableOfContentsEntryKey"
    const val chosenHighlightGroupKey = "chosenHighlightGroupKey"
    const val chosenHighlightAnnotationIndexKey = "chosenHighlightAnnotationIndexKey"
    const val chosenHighlightBoundsKey = "chosenHighlightBoundsKey"
    const val tableOfContentsExpandedPathsKey = "tableOfContentsExpandedPathsKey"
    const val tableOfContentsScrollPositionKey = "tableOfContentsScrollPositionKey"
    const val tableOfContentsScrollOffsetKey = "tableOfContentsScrollOffsetKey"
    const val tableOfContentsQueryKey = "tableOfContentsQueryKey"
    const val searchResultPageNumberKey = "searchResultPageNumberKey"
    const val fileHashKey = "fileHashKey"
    const val searchResultKey = "searchInput"
    const val linkResultKey = "linkResult"
    const val searchQueryKey = "searchQuery"
    const val searchQueryResultKey = "searchQueryResult"
    const val searchIgnoreAccentsKey = "searchIgnoreAccents"
    const val resultPositionInListKey = "searchResultPositionKey"
    const val filePathKey = "filePathKey"
    const val chosenPageIndexKey = "chosenPageIndexKey"
    const val incognitoKey = "incognito"
}

fun Intent.grantPdfReadAccess(uriString: String?) {
    val uri = uriString?.let(Uri::parse) ?: return
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
        return
    }
    clipData = ClipData.newRawUri(null, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
