package fr.cestnous.travelwow.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.cestnous.travelwow.data.model.TravelPhoto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PhotoRepository {
    private val db = FirebaseFirestore.getInstance()
    private val photosCollection = db.collection("photos")

    // Flux de toutes les photos (pour l'accueil en temps réel)
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

    // Flux des photos d'un utilisateur (pour le profil en temps réel)
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

    // Nécessaire pour les filtres et la recherche
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

    suspend fun toggleLike(photoId: String, userId: String) {
        val docRef = photosCollection.document(photoId)
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                @Suppress("UNCHECKED_CAST")
                val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                
                if (likedBy.contains(userId)) {
                    transaction.update(docRef, "likedBy", FieldValue.arrayRemove(userId))
                    transaction.update(docRef, "likesCount", FieldValue.increment(-1))
                } else {
                    transaction.update(docRef, "likedBy", FieldValue.arrayUnion(userId))
                    transaction.update(docRef, "likesCount", FieldValue.increment(1))
                }
            }.await()
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Erreur toggleLike: ${e.message}")
        }
    }
}
