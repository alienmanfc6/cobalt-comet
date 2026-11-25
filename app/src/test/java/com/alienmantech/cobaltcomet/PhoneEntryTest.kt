package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.PhoneEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneEntryTest {
    @Test
    fun toJsonAndFromJson_roundTripPhoneEntry() {
        val entry = PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE)

        val decoded = PhoneEntry.fromJson(entry.toJson())

        assertEquals(entry, decoded)
    }

    @Test
    fun fromJson_rejectsBlankLabelOrNumber() {
        val blankLabel = PhoneEntry.fromJson(JSONObject("""{"label":"","number":"5551234567"}"""))
        val blankNumber = PhoneEntry.fromJson(JSONObject("""{"label":"Driver One","number":" "}"""))

        assertNull(blankLabel)
        assertNull(blankNumber)
    }

    @Test
    fun fromJson_defaultsUnknownTypeToPhone() {
        val decoded = PhoneEntry.fromJson(
            JSONObject("""{"label":"Driver One","number":"5551234567","type":"SATELLITE"}""")
        )

        assertEquals(
            PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE),
            decoded
        )
    }

    @Test
    fun fromJson_filtersLegacyBluetoothEntries() {
        val decoded = PhoneEntry.fromJson(
            JSONObject("""{"label":"Truck Radio","number":"AA:BB:CC:DD:EE:FF","type":"BLUETOOTH"}""")
        )

        assertNull(decoded)
    }
}
