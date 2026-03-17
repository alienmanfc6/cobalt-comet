package com.alienmantech.cobaltcomet.models

import org.json.JSONObject

data class PhoneEntry(
    val label: String,
    val number: String,
    val type: ContactType = ContactType.PHONE
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_LABEL, label)
            put(KEY_NUMBER, number)
            put(KEY_TYPE, type.name)
        }
    }

    companion object {
        private const val KEY_LABEL = "label"
        private const val KEY_NUMBER = "number"
        private const val KEY_TYPE = "type"

        fun fromJson(json: JSONObject): PhoneEntry? {
            val label = json.optString(KEY_LABEL, "").trim()
            val number = json.optString(KEY_NUMBER, "").trim()
            if (label.isEmpty() || number.isEmpty()) {
                return null
            }
            val storedType = json.optString(KEY_TYPE, ContactType.PHONE.name)
            if (storedType == "BLUETOOTH") {
                return null
            }

            val type = ContactType.values().firstOrNull { it.name == storedType } ?: ContactType.PHONE
            return PhoneEntry(label, number, type)
        }
    }
}

enum class ContactType {
    PHONE
}
