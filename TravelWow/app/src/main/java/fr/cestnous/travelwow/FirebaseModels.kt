package fr.cestnous.travelwow

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Model representing a User in Firestore.
 * Path: /travelpath/{userId}
 */
data class FirebaseUser(
    @DocumentId val id: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val settings: FirebaseUserSettings = FirebaseUserSettings()
)

/**
 * Model representing user preferences and settings.
 */
data class FirebaseUserSettings(
    val followersPostsNotifications: Boolean = true,
    val likesNotifications: Boolean = true,
    val commentsNotifications: Boolean = true
)

/**
 * Model representing a Post (Parcours) in Firestore.
 * Path: /travelpath_posts/{postId}
 */
data class FirebasePost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val title: String = "",
    val locationName: String = "",
    val description: String = "",
    val mainImageUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    @get:com.google.firebase.firestore.Exclude val steps: List<FirebaseStep> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val tags: List<String> = emptyList()
)

/**
 * Model representing a Step within a Post.
 * Path: /travelpath_posts/{postId}/steps/{stepId}
 */
data class FirebaseStep(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val order: Int = 0, // Used for reordering logic
    val imageUrls: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model representing a Comment on a Post.
 * Path: /travelpath/posts/{postId}/comments/{commentId}
 */
data class FirebaseComment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val likesCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model for user interactions (Like/Follow)
 */
data class FirebaseInteraction(
    @DocumentId val id: String = "",
    val type: InteractionType = InteractionType.LIKE,
    val fromUserId: String = "",
    val targetId: String = "", // PostId or UserId
    val timestamp: Timestamp = Timestamp.now()
)

enum class InteractionType {
    LIKE, FOLLOW, BOOKMARK
}
