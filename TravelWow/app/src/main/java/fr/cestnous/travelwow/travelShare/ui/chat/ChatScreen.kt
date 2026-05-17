package fr.cestnous.travelwow.travelShare.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelPath.data.FirebasePost
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import fr.cestnous.travelwow.travelShare.data.model.ChatMessage
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.repository.PhotoRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    targetId: String,
    targetName: String,
    isGroup: Boolean,
    onBack: () -> Unit,
    onPhotoClick: (TravelPhoto) -> Unit,
    onPostClick: (FirebasePost) -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val listState = rememberLazyListState()

    LaunchedEffect(targetId) {
        viewModel.observeMessages(targetId, isGroup)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isGroup) targetName else "@$targetName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Votre message...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.sendMessage(targetId, text)
                                    text = ""
                                },
                                enabled = text.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    isMe = message.senderId == currentUserId,
                    showSenderName = isGroup,
                    onPhotoClick = onPhotoClick,
                    onPostClick = onPostClick
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    showSenderName: Boolean,
    onPhotoClick: (TravelPhoto) -> Unit,
    onPostClick: (FirebasePost) -> Unit
) {
    val photoRepository = remember { PhotoRepository() }
    val db = remember { Firebase.firestore }
    var sharedPhoto by remember { mutableStateOf<TravelPhoto?>(null) }
    var sharedPost by remember { mutableStateOf<FirebasePost?>(null) }

    LaunchedEffect(message.photoId) {
        if (message.photoId != null) {
            // First check photos
            val photos = photoRepository.getPublicPhotos()
            sharedPhoto = photos.find { it.id == message.photoId }
            
            // If not found in photos, check travelpath_posts
            if (sharedPhoto == null) {
                try {
                    val snapshot = db.collection("travelpath_posts").document(message.photoId).get().await()
                    if (snapshot.exists()) {
                        sharedPost = snapshot.toObject(FirebasePost::class.java)
                    }
                } catch (e: Exception) {
                    // Silently fail or log
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (showSenderName && !isMe) {
            Text(message.senderName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 16.dp
            )
        ) {
            val padding = if (message.photoId != null) 4.dp else 12.dp
            Column(modifier = Modifier.padding(padding)) {
                // Photo Preview
                sharedPhoto?.let { photo ->
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(bottom = if (message.message.isNotBlank()) 4.dp else 0.dp)
                            .clickable { onPhotoClick(photo) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column {
                            AsyncImage(
                                model = photo.imageUrls.firstOrNull(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                photo.locationName,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(4.dp),
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Route (Post) Preview
                sharedPost?.let { post ->
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(bottom = if (message.message.isNotBlank()) 4.dp else 0.dp)
                            .clickable { onPostClick(post) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column {
                            AsyncImage(
                                model = post.mainImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                post.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                post.locationName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = (if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                if (message.message.isNotBlank()) {
                    Text(
                        text = message.message,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
