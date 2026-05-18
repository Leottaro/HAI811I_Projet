package fr.cestnous.travelwow.travelShare.data.model

import com.google.firebase.Timestamp
import fr.cestnous.travelwow.travelPath.data.FirebaseUserSettings

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val photoUrl: String? = null, // Sync with travelPath
    val bio: String = "",
    val friends: List<String> = emptyList(),
    val fcmToken: String? = null,
    val followersCount: Int = 0, // Sync with travelPath
    val followingCount: Int = 0, // Sync with travelPath
    val createdAt: Timestamp = Timestamp.now(), // Sync with travelPath
    val settings: FirebaseUserSettings = FirebaseUserSettings(), // Sync with travelPath
    
    // travelShare specific notification flags
    val notifyMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true
)
