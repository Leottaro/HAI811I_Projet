package fr.cestnous.travelwow

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SharePostDialog(
    post: FirebasePost,
    onDismiss: () -> Unit,
    currentUserProfile: FirebaseUser? = null
) {
    val db = remember { Firebase.firestore }
    val auth = remember { Firebase.auth }
    val currentUser = auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var followers by remember { mutableStateOf<List<FirebaseUser>>(emptyList()) }
    var following by remember { mutableStateOf<List<FirebaseUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            try {
                // Fetch following IDs
                val followingIds = db.collection("travelpath")
                    .document(currentUser.uid)
                    .collection("following")
                    .get()
                    .await()
                    .documents.map { it.id }

                // Fetch follower IDs
                val followerIds = db.collection("travelpath")
                    .document(currentUser.uid)
                    .collection("followers")
                    .get()
                    .await()
                    .documents.map { it.id }

                val allUserIds = (followingIds + followerIds).distinct()

                if (allUserIds.isNotEmpty()) {
                    // Fetch profile details for these IDs in chunks
                    val profiles = mutableListOf<FirebaseUser>()
                    allUserIds.chunked(10).forEach { chunk ->
                        val snapshot = db.collection("travelpath")
                            .whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .await()
                        profiles.addAll(snapshot.toObjects(FirebaseUser::class.java))
                    }
                    
                    following = profiles.filter { followingIds.contains(it.id) }
                    followers = profiles.filter { followerIds.contains(it.id) }
                }
            } catch (e: Exception) {
                Log.e("SharePostDialog", "Error fetching connections", e)
            } finally {
                isLoading = false
            }
        }
    }

    val filteredList = (following + followers).distinctBy { it.id }.filter {
        it.username.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Partager ce parcours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un ami...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Button(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Regarde ce parcours sur TravelWow : ${post.title}\n${post.description ?: ""}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_switch), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Partager à l'extérieur")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun ami trouvé", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredList) { user ->
                            UserShareItem(
                                user = user,
                                onShare = {
                                    coroutineScope.launch {
                                        try {
                                            val senderName = currentUserProfile?.username ?: currentUser?.displayName ?: "Un voyageur"
                                            val notification = FirebaseNotification(
                                                recipientId = user.id,
                                                senderId = currentUser?.uid ?: "",
                                                senderName = senderName,
                                                senderPhotoUrl = currentUserProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                                                type = NotificationType.SHARE_POST,
                                                targetId = post.id,
                                                title = "Nouveau parcours partagé !",
                                                message = "$senderName vous a partagé le parcours \"${post.title}\"."
                                            )
                                            db.collection("notifications").add(notification)
                                            onDismiss()
                                        } catch (e: Exception) {
                                            Log.e("SharePostDialog", "Error sharing post", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun UserShareItem(user: FirebaseUser, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShare() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (user.photoUrl != null) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(user.username.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        TextButton(onClick = onShare) {
            Text("Envoyer")
        }
    }
}
