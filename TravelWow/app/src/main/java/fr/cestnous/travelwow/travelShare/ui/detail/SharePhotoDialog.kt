package fr.cestnous.travelwow.travelShare.ui.detail

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.repository.ChatRepository
import fr.cestnous.travelwow.travelShare.ui.social.ChatListViewModel
import kotlinx.coroutines.launch

@Composable
fun SharePhotoDialog(
    photo: TravelPhoto,
    onDismiss: () -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
        title = {
            Column {
                Text("Partager la publication", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Amis", modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Groupes", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                if (selectedTab == 0) {
                    val filteredFriends = friends.filter { it.username.contains(searchQuery, ignoreCase = true) }
                    if (filteredFriends.isEmpty()) {
                        Text("Aucun ami trouvé", modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn {
                            items(filteredFriends) { friend ->
                                ShareItem(name = friend.username, imageUrl = friend.profileImageUrl) {
                                    coroutineScope.launch {
                                        if (currentUser != null) {
                                            val result = chatRepository.sendMessage(
                                                senderId = currentUser.uid,
                                                senderName = currentUser.displayName ?: currentUser.email ?: "Moi",
                                                receiverId = friend.uid,
                                                text = "Regarde ce voyage à ${photo.locationName} !",
                                                photoId = photo.id
                                            )
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Partagé avec ${friend.username}", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } else {
                                                Toast.makeText(context, "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
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
                                ShareItem(name = group.name, imageUrl = null) {
                                    coroutineScope.launch {
                                        if (currentUser != null) {
                                            val result = chatRepository.sendGroupMessage(
                                                groupId = group.id,
                                                senderId = currentUser.uid,
                                                senderName = currentUser.displayName ?: currentUser.email ?: "Moi",
                                                text = "Regarde ce voyage à ${photo.locationName} !",
                                                photoId = photo.id
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
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ShareItem(name: String, imageUrl: String?, onShare: () -> Unit) {
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
        Spacer(Modifier.width(12.dp))
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Button(onClick = onShare, shape = RoundedCornerShape(8.dp)) {
            Text("Envoyer")
        }
    }
}
