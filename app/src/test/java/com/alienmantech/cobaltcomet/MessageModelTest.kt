package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.MessageModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageModelTest {
    @Test
    fun addText_ignoresNullAndEmptyValues() {
        val message = MessageModel()

        message.addText(null)
        message.addText("")
        message.addText("Coffee Shop")

        assertEquals(listOf("Coffee Shop"), message.textList)
    }

    @Test
    fun toJsonAndFromJson_roundTripAllFields() {
        val original = MessageModel(
            textList = mutableListOf("Meet here", "Bring coffee"),
            url = "https://maps.app.goo.gl/example",
            lat = "37.4219983",
            lng = "-122.084",
            locationName = "Googleplex",
            from = "5551234567",
            receivedAt = 1234L
        )

        val decoded = MessageModel().apply { fromJson(original.toJson()) }

        assertEquals(original, decoded)
    }

    @Test
    fun fromJson_filtersBlankTextEntriesAndTrimsStrings() {
        val json = JSONObject(
            """
            {
              "text": ["Meet here", "", "  ", "Bring coffee"],
              "url": " https://example.com ",
              "locationName": " Coffee Shop "
            }
            """.trimIndent()
        )

        val message = MessageModel().apply { fromJson(json) }

        assertEquals(listOf("Meet here", "Bring coffee"), message.textList)
        assertEquals("https://example.com", message.url)
        assertEquals("Coffee Shop", message.locationName)
    }

    @Test
    fun toString_producesValidEscapedJson() {
        val original = MessageModel(
            textList = mutableListOf("Line 1\nLine 2", "Quote \"here\""),
            url = "https://example.com/a\\b",
            locationName = "Cafe \"Blue\"",
            from = "555\t1234"
        )

        val decoded = MessageModel().apply { fromJson(original.toString()) }

        assertEquals(original, decoded)
    }

    @Test
    fun emptyModel_omitsEmptyFieldsInJsonRepresentations() {
        val message = MessageModel()
        val json = message.toJson()
        val jsonString = message.toString()

        assertEquals(0, json.length())
        assertEquals("{}", jsonString)
        assertFalse(json.has("url"))
        assertFalse(json.has("receivedAt"))
    }

    @Test
    fun toString_includesTimestampOnlyWhenPositive() {
        val withoutTimestamp = MessageModel(locationName = "Coffee Shop").toString()
        val withTimestamp = MessageModel(locationName = "Coffee Shop", receivedAt = 42L).toString()

        assertFalse(withoutTimestamp.contains("receivedAt"))
        assertTrue(withTimestamp.contains("\"receivedAt\":42"))
    }
}
