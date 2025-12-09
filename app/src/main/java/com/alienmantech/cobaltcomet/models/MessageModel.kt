package com.alienmantech.cobaltcomet.models

import org.json.JSONArray
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
data class MessageModel(
    var textList: MutableList<String> = mutableListOf(),
    var url: String = "",
    var lat: String = "",
    var lng: String = "",
    var locationName: String = ""
) {

    fun addText(text: String) {
        textList.add(text)
    }

    fun fromJson(data: String) {
        fromJson(JSONObject(data))
    }

    fun fromJson(jData: JSONObject) {
        textList = mutableListOf()
        jData.optJSONArray("text")?.let {
            for (i in 0 until it.length()) {
                textList.add(it.optString(i))
            }
        }

        url = jData.optString("url")
        lat = jData.optString("lat")
        lng = jData.optString("lng")
        locationName = jData.optString("locationName")
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
        return jData
    }

    override fun toString(): String {
        return toJson().toString()
    }
}