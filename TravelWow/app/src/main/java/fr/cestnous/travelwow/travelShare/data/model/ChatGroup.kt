package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ChatGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(), // Liste des UIDs des membres
    val createdBy: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
