package com.alienmantech.cobaltcomet.models

import org.json.JSONArray
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
data class MessageModel(
    var textList: MutableList<String> = mutableListOf(),
    var url: String = "",
    var lat: String = "",
    var lng: String = "",
    var locationName: String = "",
    var from: String = "",
    var receivedAt: Long = 0L
) {

    fun addText(text: String?) {
        if (text.isNullOrEmpty()) {
            return
        }
        textList.add(text)
    }

    fun fromJson(data: String) {
        fromJson(JSONObject(data))
    }

    fun fromJson(jData: JSONObject) {
        textList = mutableListOf()
        jData.optJSONArray("text")?.let {
            for (i in 0 until it.length()) {
                it.optString(i).takeIf(String::isNotBlank)?.let(textList::add)
            }
        }

        url = jData.optString("url").trim()
        lat = jData.optString("lat").trim()
        lng = jData.optString("lng").trim()
        locationName = jData.optString("locationName").trim()
        from = jData.optString("from").trim()
        receivedAt = jData.optLong("receivedAt")
    }

    fun toJson(): JSONObject {
        val jData = JSONObject()

        if (textList.isNotEmpty()) {
            val jTextArray = JSONArray()
            for (text in textList) {
                jTextArray.put(text)
            }
            jData.put("text", jTextArray)
        }

        if (url.isNotEmpty()) {
            jData.put("url", url)
        }
        if (lat.isNotEmpty()) {
            jData.put("lat", lat)
        }
        if (lng.isNotEmpty()) {
            jData.put("lng", lng)
        }
        if (locationName.isNotEmpty()) {
            jData.put("locationName", locationName)
        }
        if (from.isNotEmpty()) {
            jData.put("from", from)
        }
        if (receivedAt > 0) {
            jData.put("receivedAt", receivedAt)
        }
        return jData
    }

    override fun toString(): String {
        val fields = mutableListOf<String>()

        if (textList.isNotEmpty()) {
            val textJson = textList.joinToString(
                prefix = "[",
                postfix = "]"
            ) { "\"${it.escapeJson()}\"" }
            fields.add("\"text\":$textJson")
        }
        if (url.isNotEmpty()) {
            fields.add("\"url\":\"${url.escapeJson()}\"")
        }
        if (lat.isNotEmpty()) {
            fields.add("\"lat\":\"${lat.escapeJson()}\"")
        }
        if (lng.isNotEmpty()) {
            fields.add("\"lng\":\"${lng.escapeJson()}\"")
        }
        if (locationName.isNotEmpty()) {
            fields.add("\"locationName\":\"${locationName.escapeJson()}\"")
        }
        if (from.isNotEmpty()) {
            fields.add("\"from\":\"${from.escapeJson()}\"")
        }
        if (receivedAt > 0) {
            fields.add("\"receivedAt\":$receivedAt")
        }

        return fields.joinToString(prefix = "{", postfix = "}")
    }

    private fun String.escapeJson(): String {
        return buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
