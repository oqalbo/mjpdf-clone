// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WeakRegistryTest {
    @Test
    fun removalUsesIdentityAndIsIdempotent() {
        val registry = WeakRegistry<Token>()
        val first = Token(1)
        val second = Token(1)
        registry.add(first)
        registry.add(second)
        registry.remove(first)
        registry.remove(first)

        assertEquals(1, registry.snapshot().size)
        assertSame(second, registry.snapshot().single())
    }

    @Test
    fun observationsAreDetachedOrderedSnapshots() {
        val registry = WeakRegistry<Token>()
        val first = Token(1)
        val second = Token(2)
        registry.add(first)
        val earlier = registry.snapshot()
        registry.add(second)
        assertEquals(listOf(first, second), registry.snapshot())
        registry.remove(first)

        assertEquals(listOf(first), earlier)
        assertEquals(listOf(second), registry.snapshot())
    }

    private data class Token(val id: Int)
}
