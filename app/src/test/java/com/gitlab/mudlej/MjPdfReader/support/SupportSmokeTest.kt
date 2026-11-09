// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.support

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeTextFormatter
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SupportSmokeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scannedHomeItemUsesFrameworkUriSignatures() {
        val item = HomeItem.fromScan(ScannedPdfEntry("/books/My Book.pdf", 24L, 0L, null))

        assertEquals("file:///books/My%20Book.pdf", item.stableKey)
        assertEquals("file", item.uri.scheme)
        assertEquals("My Book.pdf", item.uri.lastPathSegment)
        assertEquals("My Book", item.title)
    }

    @Test
    fun uriParsingSeparatesDecodedComponentsAndEncodedSegments() {
        val uri = Uri.parse("https://host/books/a%2Fb.pdf?q=a%20b+c#page%202")

        assertEquals("https", uri.scheme)
        assertEquals("host", uri.authority)
        assertEquals("/books/a/b.pdf", uri.path)
        assertEquals(listOf("books", "a/b.pdf"), uri.pathSegments)
        assertEquals("q=a b+c", uri.query)
        assertEquals("page 2", uri.fragment)
        assertEquals("//host/books/a/b.pdf?q=a b+c", uri.schemeSpecificPart)
        assertTrue(uri.isAbsolute)
        assertTrue(uri.isHierarchical)
        assertFalse(uri.isOpaque)
        assertFalse(uri.isRelative)
    }

    @Test
    fun opaqueAndRelativeUrisKeepTheirDistinctShapes() {
        val opaque = Uri.parse("mailto:reader@example.com?subject=Book#mail")
        assertTrue(opaque.isOpaque)
        assertNull(opaque.path)
        assertNull(opaque.authority)
        assertNull(opaque.query)
        assertEquals(emptyList<String>(), opaque.pathSegments)
        assertEquals("reader@example.com?subject=Book", opaque.schemeSpecificPart)

        val relative = Uri.parse("books/chapter.pdf?q#f")
        assertTrue(relative.isRelative)
        assertTrue(relative.isHierarchical)
        assertNull(relative.scheme)
        assertEquals("books/chapter.pdf", relative.path)
        assertEquals("chapter.pdf", relative.lastPathSegment)
        assertEquals("q", relative.query)
        assertNull(Uri.parse("content://library/").lastPathSegment)
    }

    @Test
    fun uriEncodingPreservesUtf8AndTreatsPlusAsLiteral() {
        val raw = "a b+c/é~!*'()"
        val encoded = "a%20b%2Bc%2F%C3%A9~!*'()"
        assertEquals(encoded, Uri.encode(raw))
        assertEquals(raw, Uri.decode(encoded))
        assertEquals("a+b c", Uri.decode("a+b%20c"))
    }

    @Test
    fun malformedPercentEscapesDecodeToTheReplacementCharacterInsteadOfThrowing() {
        assertEquals("100�.pdf", Uri.decode("100%.pdf"))
        assertEquals("end�", Uri.decode("end%"))
        assertEquals("a�zz b", Uri.decode("a%zz%20b"))
        assertEquals("100�.pdf", Uri.parse("file:///books/100%.pdf").lastPathSegment)
    }

    @Test
    fun builderEncodesSegmentsWithoutDoubleEncodingExistingPaths() {
        val uri = Uri.Builder().scheme("CONTENT").authority("library").path("books folder")
            .appendPath("a/b.pdf").appendEncodedPath("part%202/end").build()

        assertEquals("CONTENT://library/books%20folder/a%2Fb.pdf/part%202/end", uri.toString())
        assertEquals(listOf("books folder", "a/b.pdf", "part 2", "end"), uri.pathSegments)
        val normalized = uri.normalizeScheme()
        assertEquals("content://library/books%20folder/a%2Fb.pdf/part%202/end", normalized.toString())
        assertEquals(normalized, Uri.parse(normalized.toString()))
        assertEquals(normalized.hashCode(), Uri.parse(normalized.toString()).hashCode())
        assertNotEquals(uri, normalized)
    }

    @Test
    fun preferencesKeepEditsPrivateUntilCommittedAndClearBeforePuts() {
        val preferences = InMemorySharedPreferences(mapOf("old" to 1, "removed" to 2))
        val editor = preferences.edit().putInt("kept", 3).clear().remove("removed")
            .putString("replaced", "discarded").remove("replaced").putString("replaced", "final")
        assertEquals(mapOf("old" to 1, "removed" to 2), preferences.snapshot())
        assertTrue(editor.commit())
        assertEquals(mapOf("kept" to 3, "replaced" to "final"), preferences.snapshot())
        preferences.edit().putString("replaced", null).putStringSet("kept", null).apply()
        assertEquals(emptyMap<String, Any?>(), preferences.snapshot())
    }

    @Test
    fun typedPreferencesRejectMismatchesAndAllowProductionRecovery() {
        val preferences = InMemorySharedPreferences(mapOf("wrong" to Any(), Preferences.highQualityKey to 1))
        val reads = listOf<() -> Any?>(
            { preferences.getBoolean("wrong", false) },
            { preferences.getInt("wrong", 0) },
            { preferences.getLong("wrong", 0L) },
            { preferences.getFloat("wrong", 0f) },
            { preferences.getString("wrong", null) },
            { preferences.getStringSet("wrong", null) },
        )
        reads.forEach { read -> assertThrows(ClassCastException::class.java) { read() } }
        assertFalse(Preferences(preferences).getHighQuality())
        assertFalse(preferences.contains(Preferences.highQualityKey))
    }

    @Test
    fun typedPreferencesReturnStoredValuesAndMissingDefaults() {
        val preferences = InMemorySharedPreferences()
        preferences.edit().putBoolean("boolean", true).putInt("int", 7).putLong("long", 8L)
            .putFloat("float", 1.5f).putString("string", "value")
            .putStringSet("set", mutableSetOf("one")).apply()
        assertTrue(preferences.getBoolean("boolean", false))
        assertEquals(7, preferences.getInt("int", 0))
        assertEquals(8L, preferences.getLong("long", 0L))
        assertEquals(1.5f, preferences.getFloat("float", 0f), 0f)
        assertEquals("value", preferences.getString("string", null))
        assertEquals(setOf("one"), preferences.getStringSet("set", null))
        assertTrue(preferences.getBoolean("missing", true))
        assertEquals(9, preferences.getInt("missing", 9))
        assertEquals(10L, preferences.getLong("missing", 10L))
        assertEquals(2.5f, preferences.getFloat("missing", 2.5f), 0f)
        assertEquals("default", preferences.getString("missing", "default"))
        assertEquals(setOf("default"), preferences.getStringSet("missing", mutableSetOf("default")))
    }

    @Test
    fun preferenceSetsAndSnapshotsCannotMutateStoredValues() {
        val original = mutableSetOf("one")
        val preferences = InMemorySharedPreferences()
        val editor = preferences.edit().putStringSet("set", original)
        original.add("two")
        editor.apply()
        preferences.getStringSet("set", null)!!.add("three")
        val snapshot = preferences.snapshot()
        (snapshot.getValue("set") as MutableSet<*>).clear()
        preferences.all.clear()
        assertEquals(setOf("one"), preferences.getStringSet("set", null))
    }

    @Test
    fun preferenceListenersSeeAppliedStateAndStopAfterUnregistering() {
        val preferences = InMemorySharedPreferences()
        val notifications = mutableListOf<Pair<String?, Map<String, Any?>>>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            assertSame(preferences, source)
            notifications.add(key to preferences.snapshot())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        preferences.edit().putInt("page", 4).apply()
        preferences.edit().putInt("page", 4).commit()
        preferences.edit().remove("absent").apply()
        preferences.edit().remove("page").apply()
        preferences.edit().putInt("page", 5).apply()
        preferences.edit().clear().putString("title", "Book").apply()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        preferences.edit().putInt("page", 6).apply()

        assertEquals(listOf("page", "page", "page", null, "title"), notifications.map { it.first })
        assertEquals(mapOf("page" to 4), notifications[0].second)
        assertEquals(emptyMap<String, Any?>(), notifications[1].second)
        assertEquals(mapOf("title" to "Book"), notifications[3].second)
    }

    @Test
    fun filesContextCreatesSeparatePersistentDirectories() {
        val files = temporaryFolder.newFolder("files")
        val cache = temporaryFolder.newFolder("cache")
        val context = FakeFilesContext(filesDir = files, cacheDir = cache)
        assertEquals(files, context.filesDir)
        assertEquals(cache, context.cacheDir)
        assertSame(context, context.applicationContext)
        val directory = context.getDir("dictionary", Context.MODE_PRIVATE)
        assertEquals(File(files, "app_dictionary"), directory)
        assertTrue(directory.isDirectory)
        File(directory, "word").writeText("definition")
        assertEquals("definition", File(context.getDir("dictionary", Context.MODE_PRIVATE), "word").readText())
        assertEquals(File(files, "no_backup"), context.noBackupFilesDir)
        assertTrue(context.noBackupFilesDir.isDirectory)
    }

    @Test
    fun arabicIndicListMarkersAcceptOptionalWhitespace() {
        assertTrue(TextModeTextFormatter.isListStart("١) item"))
        assertTrue(TextModeTextFormatter.isListStart("١)item"))
        assertFalse(TextModeTextFormatter.isListStart("١٢٣٤)item"))
    }
}
