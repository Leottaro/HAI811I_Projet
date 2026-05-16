package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Comment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
