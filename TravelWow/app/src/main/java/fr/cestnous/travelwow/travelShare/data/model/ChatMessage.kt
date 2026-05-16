package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val photoId: String? = null, // Pour partager une publication
    val timestamp: Timestamp = Timestamp.now()
)
