package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class FriendRequest(
    @DocumentId val id: String = "",
    val fromId: String = "",
    val fromUsername: String = "",
    val toId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "PENDING" // PENDING, ACCEPTED, REJECTED
)
