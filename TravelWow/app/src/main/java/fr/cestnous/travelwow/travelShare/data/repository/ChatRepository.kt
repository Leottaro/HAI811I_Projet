package fr.cestnous.travelwow.travelShare.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.cestnous.travelwow.travelShare.data.model.AppNotification
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val chatsCollection = db.collection("chats")
    private val groupsCollection = db.collection("groups")
    private val notificationsCollection = db.collection("notifications")

    private fun getChatId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_${user2}" else "${user2}_${user1}"
    }

    suspend fun sendMessage(senderId: String, senderName: String, receiverId: String, text: String, photoId: String? = null): Result<Unit> {
        return try {
            val chatId = getChatId(senderId, receiverId)
            val message = ChatMessage(
                senderId = senderId,
                senderName = senderName,
                message = text,
                photoId = photoId,
                timestamp = Timestamp.now()
            )
            chatsCollection.document(chatId).collection("messages").add(message).await()
            
            // Création de la notification dans Firestore
            val notification = AppNotification(
                toUserId = receiverId,
                fromUserName = senderName,
                title = "Message de $senderName",
                message = text,
                type = "MESSAGE"
            )
            notificationsCollection.add(notification).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Erreur: ${e.message}")
            Result.failure(e)
        }
    }

    fun getMessagesFlow(user1: String, user2: String): Flow<List<ChatMessage>> = callbackFlow {
        val chatId = getChatId(user1, user2)
        val subscription = chatsCollection.document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ChatMessage::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun createGroup(name: String, members: List<String>, creatorId: String): Result<Unit> {
        return try {
            val group = ChatGroup(name = name, members = members + creatorId, createdBy = creatorId)
            groupsCollection.add(group).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserGroupsFlow(userId: String): Flow<List<ChatGroup>> = callbackFlow {
        val subscription = groupsCollection
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ChatGroup::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun sendGroupMessage(groupId: String, senderId: String, senderName: String, text: String, photoId: String? = null): Result<Unit> {
        return try {
            val message = ChatMessage(
                senderId = senderId,
                senderName = senderName,
                message = text,
                photoId = photoId
            )
            groupsCollection.document(groupId).collection("messages").add(message).await()
            
            val groupDoc = groupsCollection.document(groupId).get().await()
            val groupName = groupDoc.getString("name") ?: "Groupe"
            val members = groupDoc.get("members") as? List<String> ?: emptyList()
            
            members.forEach { memberId ->
                if (memberId != senderId) {
                    val notification = AppNotification(
                        toUserId = memberId,
                        fromUserName = senderName,
                        title = "$groupName : $senderName",
                        message = text,
                        type = "MESSAGE"
                    )
                    notificationsCollection.add(notification)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGroupMessagesFlow(groupId: String): Flow<List<ChatMessage>> = callbackFlow {
        val subscription = groupsCollection.document(groupId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ChatMessage::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }
}
