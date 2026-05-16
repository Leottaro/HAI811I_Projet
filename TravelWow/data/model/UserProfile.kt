package fr.cestnous.travelwow.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val bio: String = "",
    val friends: List<String> = emptyList(),
    val fcmToken: String? = null, // Token pour les notifications push
    val notifyMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyLikes: Boolean = true
)
