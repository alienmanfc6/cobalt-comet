package com.alienmantech.cobaltcomet

import org.json.JSONArray
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
data class MessageModel(
    var textList: MutableList<String> = mutableListOf(),
    var url: String = ""
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
    }

    fun toJson(): JSONObject {
        val jData = JSONObject()

        val jTextArray = JSONArray()
        for (text in textList) {
            jTextArray.put(text)
        }
        jData.put("text", jTextArray)

        jData.put("url", url)
        return jData
    }

    override fun toString(): String {
        return toJson().toString()
    }
}