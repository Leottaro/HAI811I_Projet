package fr.cestnous.travelwow.travelShare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.ui.social.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (UserProfile) -> Unit,
    onGroupChatClick: (ChatGroup) -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var showCreateGroup by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateGroup = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau Groupe")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                "Messages",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Section Groupes
                if (groups.isNotEmpty()) {
                    item { 
                        Text(
                            "Groupes", 
                            style = MaterialTheme.typography.titleSmall, 
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) 
                    }
                    items(groups) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            supportingContent = { Text("${group.members.size} membres") },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Groups, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable { onGroupChatClick(group) }
                        )
                    }
                }

                // Section Amis
                item { 
                    Text(
                        "Discussions", 
                        style = MaterialTheme.typography.titleSmall, 
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) 
                }
                
                if (friends.isEmpty()) {
                    item { 
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Ajoutez des amis pour discuter", color = Color.Gray)
                        }
                    }
                } else {
                    items(friends) { friend ->
                        ListItem(
                            headlineContent = { Text("@${friend.username}") },
                            leadingContent = {
                                if (friend.profileImageUrl != null) {
                                    AsyncImage(
                                        model = friend.profileImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onChatClick(friend) }
                        )
                    }
                }
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
    var groupName by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Groupe") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Nom du groupe") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Sélectionner des membres :", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(friends) { friend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedMembers.contains(friend.uid)) selectedMembers.remove(friend.uid)
                                    else selectedMembers.add(friend.uid)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedMembers.contains(friend.uid),
                                onCheckedChange = null
                            )
                            Text("@${friend.username}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(groupName, selectedMembers.toList()) },
                enabled = groupName.isNotBlank() && selectedMembers.isNotEmpty()
            ) { Text("Créer") }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Annuler") } 
        }
    )
}
