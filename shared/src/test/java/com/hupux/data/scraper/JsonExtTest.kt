package com.hupux.data.scraper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtTest {

    private fun obj(vararg pairs: Pair<String, Any?>) = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                is String  -> put(k, v)
                is Int     -> put(k, v)
                is Long    -> put(k, v)
                is Boolean -> put(k, v)
                null       -> put(k, JsonNull)
                else       -> put(k, v.toString())
            }
        }
    }

    // ── str ────────────────────────────────────────────────────────────────

    @Test fun `str returns string value`() {
        val o = obj("name" to "科比")
        assertEquals("科比", o.str("name"))
    }

    @Test fun `str returns null for missing key`() {
        assertNull(obj().str("name"))
    }

    @Test fun `str returns null for json null`() {
        val o = obj("name" to null)
        assertNull(o.str("name"))
    }

    @Test fun `str coerces number to string`() {
        val o = obj("id" to 123)
        assertEquals("123", o.str("id"))
    }

    // ── int_ ───────────────────────────────────────────────────────────────

    @Test fun `int parses numeric string`() {
        val o = obj("count" to "42")
        assertEquals(42, o.int_("count"))
    }

    @Test fun `int returns null for non-numeric`() {
        val o = obj("count" to "abc")
        assertNull(o.int_("count"))
    }

    // ── long_ ──────────────────────────────────────────────────────────────

    @Test fun `long parses large number`() {
        val o = obj("ts" to 1793290770000L)
        assertEquals(1793290770000L, o.long_("ts"))
    }

    // ── bool_ ──────────────────────────────────────────────────────────────

    @Test fun `bool parses boolean`() {
        val o = obj("flag" to true)
        assertTrue(o.bool_("flag") == true)
    }

    @Test fun `bool parses string boolean`() {
        val o = obj("flag" to "true")
        assertTrue(o.bool_("flag") == true)
    }

    // ── arr / obj ──────────────────────────────────────────────────────────

    @Test fun `arr returns json array`() {
        val o = buildJsonObject {
            put("list", buildJsonArray { add(JsonPrimitive("a")) })
        }
        assertEquals(1, o.arr("list")?.size)
    }

    @Test fun `arr returns null for missing key`() {
        assertNull(obj().arr("list"))
    }

    // ── has ────────────────────────────────────────────────────────────────

    @Test fun `has returns true for existing key`() {
        assertTrue(obj("a" to "1").has("a"))
    }

    @Test fun `has returns false for json null`() {
        assertTrue(!obj("a" to null).has("a"))
    }

    // ── element helpers ────────────────────────────────────────────────────

    @Test fun `asStr returns string from primitive`() {
        val el: kotlinx.serialization.json.JsonElement = JsonPrimitive("hello")
        assertEquals("hello", el.asStr)
    }

    @Test fun `asLong returns long from primitive`() {
        val el: kotlinx.serialization.json.JsonElement = JsonPrimitive(42L)
        assertEquals(42L, el.asLong)
    }
}
