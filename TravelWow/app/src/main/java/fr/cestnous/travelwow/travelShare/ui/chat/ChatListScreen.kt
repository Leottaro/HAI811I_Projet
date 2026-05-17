package fr.cestnous.travelwow.travelShare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fr.cestnous.travelwow.travelPath.data.FirebasePost
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.ui.social.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (UserProfile) -> Unit,
    onGroupChatClick: (ChatGroup) -> Unit,
    onPostClick: (FirebasePost) -> Unit = {},
    viewModel: ChatListViewModel = viewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var showCreateGroup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    IconButton(onClick = { showCreateGroup = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Créer un groupe")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Groupes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            items(groups) { group ->
                ListItem(
                    headlineContent = { Text(group.name, fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(group.name.take(1).uppercase())
                        }
                    },
                    modifier = Modifier.clickable { onGroupChatClick(group) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { Text("Discussions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            items(friends) { friend ->
                ListItem(
                    headlineContent = { Text(friend.username, fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        if (friend.profileImageUrl != null) {
                            AsyncImage(
                                model = friend.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(friend.username.take(1).uppercase())
                            }
                        }
                    },
                    modifier = Modifier.clickable { onChatClick(friend) }
                )
            }
        }
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            friends = friends,
            onDismiss = { showCreateGroup = false },
            onCreate = { name, members ->
                viewModel.createGroup(name, members)
                showCreateGroup = false
            }
        )
    }
}

@Composable
fun CreateGroupDialog(
    friends: List<UserProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau groupe") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du groupe") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Membres", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(friends) { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedMembers.contains(friend.uid),
                                onCheckedChange = {
                                    if (it) selectedMembers.add(friend.uid)
                                    else selectedMembers.remove(friend.uid)
                                }
                            )
                            Text(friend.username)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedMembers.toList()) },
                enabled = name.isNotBlank() && selectedMembers.isNotEmpty()
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
