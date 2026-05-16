package fr.cestnous.travelwow.travelShare.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.cestnous.travelwow.travelShare.data.model.AppNotification
import fr.cestnous.travelwow.travelShare.data.model.FriendRequest
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FriendRepository {
    private val db = FirebaseFirestore.getInstance()
    private val requestsCollection = db.collection("friendRequests")
    private val usersCollection = db.collection("users")
    private val notificationsCollection = db.collection("notifications")

    // Envoyer une demande d'ami + notification
    suspend fun sendFriendRequest(fromUser: UserProfile, toUserId: String): Result<Unit> {
        return try {
            val existing = requestsCollection
                .whereEqualTo("fromId", fromUser.uid)
                .whereEqualTo("toId", toUserId)
                .get().await()
            
            if (!existing.isEmpty) return Result.failure(Exception("Demande déjà envoyée"))

            val request = FriendRequest(
                fromId = fromUser.uid,
                fromUsername = fromUser.username,
                toId = toUserId,
                status = "PENDING"
            )
            requestsCollection.add(request).await()

            // Notification pour le destinataire
            val notification = AppNotification(
                toUserId = toUserId,
                fromUserName = fromUser.username,
                title = "Nouvelle demande d'ami",
                message = "${fromUser.username} souhaite devenir votre ami",
                type = "FRIEND_REQUEST"
            )
            notificationsCollection.add(notification).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Accepter une demande d'ami + notification
    suspend fun acceptFriendRequest(requestId: String, userId: String, friendId: String, myUsername: String): Result<Unit> {
        return try {
            db.runBatch { batch ->
                batch.update(requestsCollection.document(requestId), "status", "ACCEPTED")
                batch.update(usersCollection.document(userId), "friends", FieldValue.arrayUnion(friendId))
                batch.update(usersCollection.document(friendId), "friends", FieldValue.arrayUnion(userId))
            }.await()

            // Notification pour l'ami qui avait envoyé la demande
            val notification = AppNotification(
                toUserId = friendId,
                fromUserName = myUsername,
                title = "Demande d'ami acceptée",
                message = "$myUsername a accepté votre demande d'ami",
                type = "FRIEND_REQUEST"
            )
            notificationsCollection.add(notification).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(requestId: String): Result<Unit> {
        return try {
            requestsCollection.document(requestId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getIncomingRequestsFlow(userId: String): Flow<List<FriendRequest>> = callbackFlow {
        val subscription = requestsCollection
            .whereEqualTo("toId", userId)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(FriendRequest::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }
}
