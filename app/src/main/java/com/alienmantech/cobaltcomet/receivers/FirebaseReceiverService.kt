package com.alienmantech.cobaltcomet.receivers

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
            //TODO: parse incoming message
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {

        }
    }
}