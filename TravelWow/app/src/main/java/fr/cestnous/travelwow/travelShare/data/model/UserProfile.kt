package fr.cestnous.travelwow.travelShare.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val bio: String = "",
    val friends: List<String> = emptyList(),
    val fcmToken: String? = null,
    val notifyMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true
)
