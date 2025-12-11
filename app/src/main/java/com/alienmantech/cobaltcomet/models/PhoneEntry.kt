package com.alienmantech.cobaltcomet.models

import org.json.JSONObject

data class PhoneEntry(
    val label: String,
    val number: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_LABEL, label)
            put(KEY_NUMBER, number)
        }
    }

    companion object {
        private const val KEY_LABEL = "label"
        private const val KEY_NUMBER = "number"

        fun fromJson(json: JSONObject): PhoneEntry? {
            val label = json.optString(KEY_LABEL, "")
            val number = json.optString(KEY_NUMBER, "")
            if (label.isEmpty() || number.isEmpty()) {
                return null
            }
            return PhoneEntry(label, number)
        }
    }
}
