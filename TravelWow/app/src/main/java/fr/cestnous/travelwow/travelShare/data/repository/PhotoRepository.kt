package fr.cestnous.travelwow.travelShare.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.cestnous.travelwow.travelShare.data.model.AppNotification
import fr.cestnous.travelwow.travelShare.data.model.Comment
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PhotoRepository {
    private val db = FirebaseFirestore.getInstance()
    private val photosCollection = db.collection("photos")
    private val notificationsCollection = db.collection("notifications")
    private val usersCollection = db.collection("users")

    fun getPhotosFlow(): Flow<List<TravelPhoto>> = callbackFlow {
        val subscription = photosCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val photos = snapshot.toObjects(TravelPhoto::class.java)
                    trySend(photos)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getUserPhotosFlow(userId: String): Flow<List<TravelPhoto>> = callbackFlow {
        val subscription = photosCollection
            .whereEqualTo("authorId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val photos = snapshot.toObjects(TravelPhoto::class.java)
                    trySend(photos.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun getPublicPhotos(): List<TravelPhoto> {
        return try {
            photosCollection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(TravelPhoto::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPhotos(query: String): List<TravelPhoto> {
        val all = getPublicPhotos()
        return all.filter {
            it.description.contains(query, ignoreCase = true) ||
            it.locationName.contains(query, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> {
        return try {
            photosCollection.document(photoId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(photoId: String, userId: String, userName: String) {
        val docRef = photosCollection.document(photoId)
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                @Suppress("UNCHECKED_CAST")
                val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val authorId = snapshot.getString("authorId") ?: ""
                val location = snapshot.getString("locationName") ?: "un voyage"
                
                if (likedBy.contains(userId)) {
                    transaction.update(docRef, "likedBy", FieldValue.arrayRemove(userId))
                    transaction.update(docRef, "likesCount", FieldValue.increment(-1))
                } else {
                    transaction.update(docRef, "likedBy", FieldValue.arrayUnion(userId))
                    transaction.update(docRef, "likesCount", FieldValue.increment(1))
                    
                    if (authorId != userId && authorId.isNotEmpty()) {
                        sendNotificationIfEnabled(authorId, userName, "Nouveau Like !", "$userName a aimé votre voyage à $location", "LIKE")
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Erreur toggleLike: ${e.message}")
        }
    }

    fun getCommentsFlow(photoId: String): Flow<List<Comment>> = callbackFlow {
        val subscription = photosCollection.document(photoId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Comment::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addComment(photoId: String, comment: Comment): Result<Unit> {
        return try {
            photosCollection.document(photoId).collection("comments").add(comment).await()
            
            val photoDoc = photosCollection.document(photoId).get().await()
            val authorId = photoDoc.getString("authorId") ?: ""
            if (authorId != comment.userId && authorId.isNotEmpty()) {
                sendNotificationIfEnabled(authorId, comment.username, "Nouveau commentaire", "${comment.username} a commenté votre photo", "COMMENT")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportPhoto(photoId: String, reporterId: String?) {
        val reportData = hashMapOf(
            "photoId" to photoId,
            "reporterId" to (reporterId ?: "Anonyme"),
            "timestamp" to Timestamp.now(),
            "status" to "En attente"
        )

        try {
            db.collection("reports").add(reportData).await()
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Erreur lors du signalement: ${e.message}")
        }
    }

    private fun sendNotificationIfEnabled(toUserId: String, fromName: String, title: String, message: String, type: String) {
        usersCollection.document(toUserId).get().addOnSuccessListener { userDoc ->
            val shouldNotify = when(type) {
                "LIKE" -> userDoc.getBoolean("notifyLikes") ?: true
                "COMMENT" -> userDoc.getBoolean("notifyComments") ?: true
                "MESSAGE" -> userDoc.getBoolean("notifyMessages") ?: true
                "FRIEND_REQUEST" -> userDoc.getBoolean("notifyFriendRequests") ?: true
                else -> true
            }

            if (shouldNotify) {
                val notification = AppNotification(
                    toUserId = toUserId,
                    fromUserName = fromName,
                    title = title,
                    message = message,
                    type = type
                )
                notificationsCollection.add(notification)
            }
        }
    }
}
