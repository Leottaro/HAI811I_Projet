package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class AppNotification(
    @DocumentId val id: String = "",
    val toUserId: String = "",
    val fromUserName: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "MESSAGE",
    val timestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("read") @set:PropertyName("read") var read: Boolean = false
)
