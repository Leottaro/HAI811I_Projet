package fr.cestnous.travelwow

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
            Log.d("UserDetailDialog", "--- Loading User Details ---")
            Log.d("UserDetailDialog", "UserId: $userId")
            Log.d("UserDetailDialog", "Fetching user from Firestore: users/$userId")
            
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

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        title = {
            Text(
                text = "Profil utilisateur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (userDetails != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile Picture
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userDetails?.photoUrl != null) {
                            AsyncImage(
                                model = userDetails?.photoUrl,
                                contentDescription = "Photo de profil",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_account_box),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Name and Email
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = userDetails?.username ?: "Utilisateur",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userDetails?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Follow/Unfollow Button
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
                                            
                                            // Update counts
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
                                            
                                            // Update counts
                                            db.collection("users").document(currentUser.uid)
                                                .update("followingCount", FieldValue.increment(1))
                                            db.collection("users").document(userId)
                                                .update("followersCount", FieldValue.increment(1))
                                            
                                            // Send Notification if enabled by recipient
                                            if (userDetails?.settings?.newFollowerNotifications == true) {
                                                val senderDoc = db.collection("users").document(currentUser.uid).get().await()
                                                val senderProfile = senderDoc.toObject(FirebaseUser::class.java)
                                                
                                                val notification = FirebaseNotification(
                                                    recipientId = userId,
                                                    senderId = currentUser.uid,
                                                    senderName = senderProfile?.username ?: "Un utilisateur",
                                                    senderPhotoUrl = senderProfile?.photoUrl,
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                        ) {
                            Text(if (isFollowing) "Suivi(e)" else "Suivre")
                        }
                    }

                    // Bio
                    if (userDetails?.bio?.isNotBlank() == true) {
                        Text(
                            text = userDetails!!.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UserStatItem(label = "Publications", value = userPostCount.toString())
                        UserStatItem(label = "Abonnés", value = userDetails?.followersCount?.toString() ?: "0")
                        UserStatItem(label = "Abonnements", value = userDetails?.followingCount?.toString() ?: "0")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // User's Posts
                    Text(
                        text = "Publications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        PostsGallery(
                            onPostClick = { /* Maybe show detail? */ },
                            viewMode = GalleryViewMode.GRID,
                            modifier = Modifier.fillMaxSize(),
                            userIdFilter = userId
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Utilisateur introuvable", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun UserStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
