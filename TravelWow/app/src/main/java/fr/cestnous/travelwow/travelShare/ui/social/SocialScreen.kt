package fr.cestnous.travelwow.travelShare.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import fr.cestnous.travelwow.travelShare.data.model.UserProfile

@Composable
fun SocialScreen(
    onChatClick: (UserProfile) -> Unit,
    viewModel: SocialViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Amis", "Découvrir", "Demandes")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> FriendsListTab(onChatClick, viewModel)
            1 -> DiscoverFriendsTab(viewModel)
            2 -> RequestsTab(viewModel)
        }
    }
}

@Composable
fun FriendsListTab(onChatClick: (UserProfile) -> Unit, viewModel: SocialViewModel) {
    val friends by viewModel.friends.collectAsState()

    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Vous n'avez pas encore d'amis.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(friends) { friend ->
                UserItem(
                    user = friend,
                    onClick = { onChatClick(friend) },
                    trailingContent = {
                        Text("Message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}

@Composable
fun DiscoverFriendsTab(viewModel: SocialViewModel) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.searchUsers(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher un pseudo...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { user ->
                    UserItem(
                        user = user,
                        trailingContent = {
                            IconButton(onClick = { viewModel.sendRequest(user) }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Ajouter", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RequestsTab(viewModel: SocialViewModel) {
    val requests by viewModel.incomingRequests.collectAsState()

    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune demande en attente.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { request ->
                ListItem(
                    headlineContent = { Text("@${request.fromUsername}") },
                    supportingContent = { Text("Souhaite devenir votre ami") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.acceptRequest(request) }) {
                                Icon(Icons.Default.Check, contentDescription = "Accepter", tint = Color.Green)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserItem(
    user: UserProfile,
    onClick: () -> Unit = {},
    trailingContent: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text("@${user.username}") },
        supportingContent = { if (user.bio.isNotEmpty()) Text(user.bio, maxLines = 1) },
        leadingContent = {
            if (user.profileImageUrl != null) {
                AsyncImage(
                    model = user.profileImageUrl,
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
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = trailingContent
    )
}
