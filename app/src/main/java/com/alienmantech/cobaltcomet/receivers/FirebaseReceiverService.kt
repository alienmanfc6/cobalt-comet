package com.alienmantech.cobaltcomet.receivers

import com.alienmantech.cobaltcomet.models.StoredFirebaseMessage
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Logger
import com.alienmantech.cobaltcomet.utils.Utils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseReceiverService: FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // save to shared pref
        Utils.saveFirebaseId(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            handleDataPayload(remoteMessage)
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {

        }
    }

    private fun handleDataPayload(remoteMessage: RemoteMessage) {
        try {
            val body = remoteMessage.data["body"] ?: remoteMessage.data["message"] ?: ""

            if (body.isEmpty()) {
                Logger.logInfo("Firebase message missing body; ignoring.")
                return
            }

            val from = remoteMessage.data["from"] ?: remoteMessage.from.orEmpty()

            Utils.saveFirebaseMessage(
                this,
                StoredFirebaseMessage(
                    from = from,
                    body = body,
                    timestamp = if (remoteMessage.sentTime != 0L) remoteMessage.sentTime else System.currentTimeMillis()
                )
            )

            if (CommunicationUtils.shouldInterceptMessage(body)) {
                CommunicationUtils.handleIncomingMessage(this, from, body)
            }
        } catch (e: Exception) {
            Logger.logError("Unable to parse firebase message.", e)
        }
    }
}