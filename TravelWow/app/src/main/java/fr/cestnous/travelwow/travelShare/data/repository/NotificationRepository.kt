package fr.cestnous.travelwow.travelShare.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import fr.cestnous.travelwow.travelShare.data.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val notificationsCollection = db.collection("notifications")

    fun getNotificationsFlow(userId: String): Flow<List<AppNotification>> = callbackFlow {
        Log.d("NotificationRepo", "Écoute des notifications pour: $userId")
        
        // On simplifie la requête pour éviter le besoin d'index composite
        val subscription = notificationsCollection
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotificationRepo", "Erreur Firestore: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.toObjects(AppNotification::class.java)
                    Log.d("NotificationRepo", "${notifications.size} notifs reçues")
                    trySend(notifications)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun markAsRead(notificationId: String) {
        try {
            notificationsCollection.document(notificationId).update("read", true).await()
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Erreur markAsRead: ${e.message}")
        }
    }
}
