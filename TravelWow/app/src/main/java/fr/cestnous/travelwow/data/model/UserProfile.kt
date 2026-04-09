package fr.cestnous.travelwow.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val bio: String = ""
)
