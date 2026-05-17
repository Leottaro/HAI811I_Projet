package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.content.Intent
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.firestore
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.repository.ChatRepository
import fr.cestnous.travelwow.travelShare.ui.social.ChatListViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SharePostDialog(
    post: FirebasePost,
    onDismiss: () -> Unit,
    currentUserProfile: FirebaseUser? = null,
    viewModel: ChatListViewModel = viewModel()
) {
    val db = remember { Firebase.firestore }
    val auth = remember { Firebase.auth }
    val currentUser = auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val chatRepository = remember { ChatRepository() }

    val friends by viewModel.friends.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

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
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Amis", modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Groupes", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (selectedTab == 0) {
                    val filteredFriends = friends.filter { it.username.contains(searchQuery, ignoreCase = true) }
                    if (filteredFriends.isEmpty()) {
                        Text("Aucun ami trouvé", modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn {
                            items(filteredFriends) { friend ->
                                UserShareItem(
                                    name = friend.username,
                                    imageUrl = friend.profileImageUrl,
                                    onShare = {
                                        coroutineScope.launch {
                                            if (currentUser != null) {
                                                val senderName = currentUserProfile?.username ?: currentUser.displayName ?: currentUser.email ?: "Moi"
                                                val result = chatRepository.sendMessage(
                                                    senderId = currentUser.uid,
                                                    senderName = senderName,
                                                    receiverId = friend.uid,
                                                    text = "Regarde ce parcours : ${post.title} !",
                                                    photoId = post.id // Using photoId field for post sharing too
                                                )
                                                if (result.isSuccess) {
                                                    // Also add a notification for the Path system
                                                    val notification = FirebaseNotification(
                                                        recipientId = friend.uid,
                                                        senderId = currentUser.uid,
                                                        senderName = senderName,
                                                        senderPhotoUrl = currentUserProfile?.photoUrl ?: currentUser.photoUrl?.toString(),
                                                        type = NotificationType.SHARE_POST,
                                                        targetId = post.id,
                                                        title = "Nouveau parcours partagé !",
                                                        message = "$senderName vous a partagé le parcours \"${post.title}\"."
                                                    )
                                                    db.collection("notifications").add(notification)
                                                    
                                                    Toast.makeText(context, "Partagé avec ${friend.username}", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val filteredGroups = groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    if (filteredGroups.isEmpty()) {
                        Text("Aucun groupe trouvé", modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn {
                            items(filteredGroups) { group ->
                                UserShareItem(
                                    name = group.name,
                                    imageUrl = null,
                                    onShare = {
                                        coroutineScope.launch {
                                            if (currentUser != null) {
                                                val senderName = currentUserProfile?.username ?: currentUser.displayName ?: currentUser.email ?: "Moi"
                                                val result = chatRepository.sendGroupMessage(
                                                    groupId = group.id,
                                                    senderId = currentUser.uid,
                                                    senderName = senderName,
                                                    text = "Regarde ce parcours : ${post.title} !",
                                                    photoId = post.id
                                                )
                                                if (result.isSuccess) {
                                                    Toast.makeText(context, "Partagé dans ${group.name}", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun UserShareItem(name: String, imageUrl: String?, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShare() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onShare) {
            Text("Envoyer")
        }
    }
}
