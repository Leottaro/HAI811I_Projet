package fr.cestnous.travelwow.travelPath.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/**
 * Model representing a User in Firestore.
 * Path: /users/{userId}
 */
data class FirebaseUser(
    @DocumentId val id: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val profileImageUrl: String? = null, // Sync with travelShare
    val fcmToken: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friends: List<String> = emptyList(), // Sync with travelShare
    val createdAt: Timestamp = Timestamp.now(),
    val settings: FirebaseUserSettings = FirebaseUserSettings(),
    
    // travelShare specific notification flags
    val notifyMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true
)

/**
 * Model representing a Liked Post for a user.
 * Path: /users/{userId}/liked_posts/{postId}
 */
data class FirebaseLikedPost(
    @DocumentId val id: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model representing user preferences and settings.
 */
data class FirebaseUserSettings(
    val followersPostsNotifications: Boolean = true,
    val likesNotifications: Boolean = true,
    val commentsNotifications: Boolean = true,
    val newFollowerNotifications: Boolean = true
)

/**
 * Model representing a Post (Parcours) in Firestore.
 * Path: /travelpath_posts/{postId}
 */
data class FirebasePost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val locationName: String = "",
    val description: String? = null,
    val mainImageUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    @get:Exclude val steps: List<FirebaseStep> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val categories: List<String> = emptyList()
)

/**
 * Model representing a Step within a Post.
 * Path: /travelpath_posts/{postId}/steps/{stepId}
 */
data class FirebaseStep(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val order: Int = 0, // Used for reordering logic
    val imageUrls: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model representing a Comment on a Post.
 * Path: /travelpath_posts/{postId}/comments/{commentId}
 */
data class FirebaseComment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val text: String = "",
    val likedByUsers: List<String> = List(0) { "" },
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model for reporting content.
 */
data class FirebaseReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetId: String = "",
    val targetType: String = "post", // "post" or "comment"
    val reason: String = "",
    val otherReason: String? = null,
    val timestamp: Timestamp = Timestamp.now()
)

enum class NotificationType {
    FOLLOW, LIKE, COMMENT, NEW_POST, SHARE_POST
}

/**
 * Model representing a Notification for the user.
 * Path: /notifications/{notificationId}
 */
data class FirebaseNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val type: NotificationType = NotificationType.FOLLOW,
    val targetId: String = "", // PostId if type is LIKE/COMMENT, else empty
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Model representing filters for posts.
 */
data class PostFilter(
    val selectedCategories: Set<String> = emptySet(),
    val minDistance: Float = 0f,
    val maxDistance: Float = 100f // Default max matches slider
)
