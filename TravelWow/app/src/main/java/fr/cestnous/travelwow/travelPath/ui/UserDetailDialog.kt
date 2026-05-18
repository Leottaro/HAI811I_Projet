package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun UserDetailDialog(
    userId: String,
    onDismiss: () -> Unit
) {
    val auth = remember { Firebase.auth }
    val currentUser = auth.currentUser
    val db = remember { Firebase.firestore }
    val coroutineScope = rememberCoroutineScope()

    var userDetails by remember { mutableStateOf<FirebaseUser?>(null) }
    var userPostCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }

    LaunchedEffect(userId, currentUser?.uid) {
        if (userId.isBlank()) {
            onDismiss()
            return@LaunchedEffect
        }
        try {
            val doc = db.collection("users")
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                userDetails = doc.toObject(FirebaseUser::class.java)
            }

            // Check if current user is following this user
            if (currentUser != null && currentUser.uid != userId) {
                val followDoc = db.collection("users")
                    .document(currentUser.uid)
                    .collection("following")
                    .document(userId)
                    .get()
                    .await()
                isFollowing = followDoc.exists()
            }

            // Fetch post count
            val countSnapshot = db.collection("travelpath_posts")
                .whereEqualTo("authorId", userId)
                .get()
                .await()
            userPostCount = countSnapshot.size()

        } catch (e: Exception) {
            Log.e("UserDetailDialog", "Error fetching user details", e)
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (userDetails != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with Background and Profile Picture
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        // Background Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                )
                        )

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }

                        // Profile Image (Overlapping)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(110.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.surface),
                            tonalElevation = 4.dp
                        ) {
                            if (userDetails?.profileImageUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(userDetails?.profileImageUrl)
                                        .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Photo de profil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (userDetails?.username ?: "?").take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // User Info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = userDetails?.username ?: "Utilisateur",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = userDetails?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Bio
                        if (userDetails?.bio?.isNotBlank() == true) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = userDetails!!.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Stats Row (Only Posts)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(min = 120.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                UserStatItem(label = "Publications", value = userPostCount.toString())
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Button
                        if (currentUser != null && currentUser.uid != userId) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val myFollowingRef = db.collection("users")
                                                .document(currentUser.uid)
                                                .collection("following")
                                                .document(userId)

                                            val theirFollowersRef = db.collection("users")
                                                .document(userId)
                                                .collection("followers")
                                                .document(currentUser.uid)

                                            if (isFollowing) {
                                                // Unfollow
                                                myFollowingRef.delete().await()
                                                theirFollowersRef.delete().await()

                                                db.collection("users").document(currentUser.uid)
                                                    .update("followingCount", FieldValue.increment(-1))
                                                db.collection("users").document(userId)
                                                    .update("followersCount", FieldValue.increment(-1))

                                                isFollowing = false
                                                userDetails = userDetails?.copy(followersCount = (userDetails?.followersCount ?: 0) - 1)
                                            } else {
                                                // Follow
                                                myFollowingRef.set(mapOf("timestamp" to FieldValue.serverTimestamp())).await()
                                                theirFollowersRef.set(mapOf("timestamp" to FieldValue.serverTimestamp())).await()

                                                db.collection("users").document(currentUser.uid)
                                                    .update("followingCount", FieldValue.increment(1))
                                                db.collection("users").document(userId)
                                                    .update("followersCount", FieldValue.increment(1))

                                                // Notification
                                                if (userDetails?.settings?.newFollowerNotifications == true) {
                                                    val senderDoc = db.collection("users").document(currentUser.uid).get().await()
                                                    val senderProfile = senderDoc.toObject(FirebaseUser::class.java)

                                                    val notification = FirebaseNotification(
                                                        recipientId = userId,
                                                        senderId = currentUser.uid,
                                                        senderName = senderProfile?.username ?: "Un voyageur",
                                                        senderPhotoUrl = userDetails?.profileImageUrl,
                                                        type = NotificationType.FOLLOW,
                                                        title = "Nouveau follower !",
                                                        message = "${senderProfile?.username ?: "Quelqu'un"} vient de s'abonner à votre profil."
                                                    )
                                                    db.collection("notifications").add(notification)
                                                }

                                                isFollowing = true
                                                userDetails = userDetails?.copy(followersCount = (userDetails?.followersCount ?: 0) + 1)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("UserDetailDialog", "Error toggling follow", e)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowing) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(
                                    if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isFollowing) "Ne plus suivre" else "Suivre",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // User's Publications Gallery
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "Publications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)) {
                            PostsGallery(
                                onPostClick = { /* User can click but it's already in a dialog */ },
                                viewMode = GalleryViewMode.GRID,
                                modifier = Modifier.fillMaxSize(),
                                userIdFilter = userId
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Utilisateur introuvable", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun UserStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
