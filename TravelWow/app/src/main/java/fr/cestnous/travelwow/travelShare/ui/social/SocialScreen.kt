package fr.cestnous.travelwow.travelShare.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.model.FriendRequest

@Composable
fun SocialScreen(
    onChatClick: (UserProfile) -> Unit,
    onPostClick: (FirebasePost) -> Unit = {},
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
            Text("Vous n'avez pas encore d'amis.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(friends) { friend ->
                UserItem(friend, onAction = { onChatClick(friend) }) {
                    Text("Discuter")
                }
            }
        }
    }
}

@Composable
fun DiscoverFriendsTab(viewModel: SocialViewModel) {
    val searchResults by viewModel.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                viewModel.searchUsers(it)
            },
            label = { Text("Rechercher des voyageurs") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(searchResults) { user ->
                UserItem(user, onAction = { viewModel.sendRequest(user) }) {
                    Text("Ajouter")
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
            Text("Aucune demande en attente.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { request ->
                ListItem(
                    headlineContent = { Text(request.fromUsername, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Souhaite devenir votre ami") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(request.fromUsername.take(1).uppercase())
                        }
                    },
                    trailingContent = {
                        Button(
                            onClick = { viewModel.acceptRequest(request) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Accepter")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserItem(user: UserProfile, onAction: () -> Unit, actionLabel: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (user.profileImageUrl != null) {
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(user.username.take(1).uppercase())
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, style = MaterialTheme.typography.titleMedium)
            if (user.bio.isNotBlank()) {
                Text(user.bio, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        
        Button(onClick = onAction) {
            actionLabel()
        }
    }
}
