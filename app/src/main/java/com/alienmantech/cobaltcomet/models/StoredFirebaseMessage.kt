package com.alienmantech.cobaltcomet.models

import org.json.JSONObject

data class StoredFirebaseMessage(
    val from: String = "",
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("from", from)
        jsonObject.put("body", body)
        jsonObject.put("timestamp", timestamp)
        return jsonObject
    }

    companion object {
        fun fromJson(jsonObject: JSONObject?): StoredFirebaseMessage? {
            jsonObject ?: return null

            val body = jsonObject.optString("body", "")
            if (body.isEmpty()) {
                return null
            }

            return StoredFirebaseMessage(
                from = jsonObject.optString("from", ""),
                body = body,
                timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
