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
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

@Composable
fun UserDetailDialog(
    userId: String,
    onDismiss: () -> Unit
) {
    var userDetails by remember { mutableStateOf<fr.cestnous.travelwow.FirebaseUser?>(null) }
    var userPostCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        try {
            Log.d("UserDetailDialog", "--- Loading User Details ---")
            Log.d("UserDetailDialog", "UserId: $userId")
            Log.d("UserDetailDialog", "Fetching user from Firestore: travelpath/$userId")
            
            val db = Firebase.firestore
            val doc = db.collection("travelpath")
                .document(userId)
                .get()
                .await()
            
            if (doc.exists()) {
                userDetails = doc.toObject(fr.cestnous.travelwow.FirebaseUser::class.java)
                Log.d("UserDetailDialog", "User data retrieved successfully")
                Log.d("UserDetailDialog", "Username: ${userDetails?.username}")
                Log.d("UserDetailDialog", "PhotoUrl: ${userDetails?.photoUrl}")
                Log.d("UserDetailDialog", "Bio length: ${userDetails?.bio?.length ?: 0}")
            } else {
                Log.w("UserDetailDialog", "No document found in Firestore for userId: $userId")
            }

            // Fetch post count
            Log.d("UserDetailDialog", "Fetching post count for user...")
            val countSnapshot = db.collection("travelpath_posts")
                .whereEqualTo("authorId", userId)
                .get()
                .await()
            userPostCount = countSnapshot.size()
            Log.d("UserDetailDialog", "Found $userPostCount posts for user")
            
        } catch (e: Exception) {
            Log.e("UserDetailDialog", "CRITICAL ERROR fetching user details for ID: $userId", e)
        } finally {
            isLoading = false
            Log.d("UserDetailDialog", "--- Loading Finished ---")
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
