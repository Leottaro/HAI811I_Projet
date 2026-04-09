package fr.cestnous.travelwow.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class TravelPhoto(
    @DocumentId val id: String = "",
    val imageUrls: List<String> = emptyList(),
    val description: String = "",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val authorId: String = "",
    val authorName: String = "Anonyme",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList(), // Liste des UIDs des utilisateurs qui ont aimé
    val placeType: String = "Nature",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = true
)
