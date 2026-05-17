package fr.cestnous.travelwow.travelPath.service

import fr.cestnous.travelwow.*
import fr.cestnous.travelwow.travelPath.*
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TravelWowMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Update token in Firestore if user is logged in
        val userId = Firebase.auth.currentUser?.uid
        if (userId != null) {
            updateTokenInFirestore(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received from: ${message.from}")

        message.notification?.let {
            sendNotification(it.title ?: "TravelWow", it.body ?: "")
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, fr.cestnous.travelwow.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "travelwow_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_home) // Use a better icon if available
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TravelWow Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        fun sendLocalNotification(context: Context, title: String, messageBody: String) {
            val intent = Intent(context, fr.cestnous.travelwow.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "travelwow_notifications"
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "TravelWow Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }

        fun updateTokenInFirestore(userId: String, token: String) {
            if (userId.isBlank()) return
            val db = Firebase.firestore
            db.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "Token updated in Firestore") }
                .addOnFailureListener { e -> Log.w("FCM", "Error updating token", e) }
        }
    }
}
