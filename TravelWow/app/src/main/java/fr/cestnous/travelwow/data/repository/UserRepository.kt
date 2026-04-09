package fr.cestnous.travelwow.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import fr.cestnous.travelwow.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun isAnonymous(): Boolean = auth.currentUser?.isAnonymous ?: true

    suspend fun signInAnonymously(): Result<FirebaseUser?> {
        return try {
            val result = auth.signInAnonymously().await()
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUsernameUnique(username: String): Boolean {
        val query = usersCollection.whereEqualTo("username", username).get().await()
        return query.isEmpty
    }

    suspend fun createUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            usersCollection.document(profile.uid).set(profile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            usersCollection.document(uid).get().await().toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(uid: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            usersCollection.document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
