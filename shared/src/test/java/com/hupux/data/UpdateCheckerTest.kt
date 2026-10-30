package com.hupux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    // ── normalize ──────────────────────────────────────────────────────────

    @Test fun `normalize strips v prefix`() {
        assertEquals("1.8.17", normalize("v1.8.17"))
        assertEquals("1.8.17", normalize("V1.8.17"))
    }

    @Test fun `normalize strips build suffix`() {
        assertEquals("1.8.17", normalize("1.8.17 (30)"))
    }

    @Test fun `normalize handles plain version`() {
        assertEquals("1.8.17", normalize("1.8.17"))
    }

    @Test fun `normalize trims whitespace`() {
        assertEquals("1.8.17", normalize("  v1.8.17  "))
    }

    // ── compareVersions ────────────────────────────────────────────────────

    @Test fun `equal versions return 0`() {
        assertEquals(0, compareVersions("1.8.17", "1.8.17"))
        assertEquals(0, compareVersions("1.8", "1.8.0"))
    }

    @Test fun `higher version returns positive`() {
        assertTrue(compareVersions("1.8.18", "1.8.17") > 0)
        assertTrue(compareVersions("1.9.0", "1.8.17") > 0)
        assertTrue(compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test fun `lower version returns negative`() {
        assertTrue(compareVersions("1.8.16", "1.8.17") < 0)
        assertTrue(compareVersions("1.7.0", "1.8.17") < 0)
    }

    @Test fun `different segment counts pad with zero`() {
        assertEquals(0, compareVersions("1.8", "1.8.0"))
        assertTrue(compareVersions("1.8", "1.8.1") < 0)
    }

    @Test fun `non-numeric segments treated as zero`() {
        assertEquals(0, compareVersions("1.8.0-beta", "1.8.0"))
    }
}
