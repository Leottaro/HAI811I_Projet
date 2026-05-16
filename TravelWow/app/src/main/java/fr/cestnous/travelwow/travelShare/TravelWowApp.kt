package fr.cestnous.travelwow.travelShare

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.repository.NotificationRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import fr.cestnous.travelwow.travelShare.ui.chat.ChatListScreen
import fr.cestnous.travelwow.travelShare.ui.chat.ChatScreen
import fr.cestnous.travelwow.travelShare.ui.chat.ChatViewModel
import fr.cestnous.travelwow.travelShare.ui.components.PlaceholderScreen
import fr.cestnous.travelwow.travelShare.ui.detail.PhotoDetailScreen
import fr.cestnous.travelwow.travelShare.ui.favorites.FavoritesScreen
import fr.cestnous.travelwow.travelShare.ui.feed.FeedScreen
import fr.cestnous.travelwow.travelShare.ui.map.MapScreen
import fr.cestnous.travelwow.travelShare.ui.profile.ProfileScreen
import fr.cestnous.travelwow.travelShare.ui.profile.SettingsScreen
import fr.cestnous.travelwow.travelShare.ui.social.ChatListViewModel
import fr.cestnous.travelwow.travelShare.ui.social.SocialScreen
import fr.cestnous.travelwow.travelShare.ui.social.SocialViewModel
import fr.cestnous.travelwow.travelShare.ui.upload.UploadScreen
import kotlinx.coroutines.flow.collectLatest
import kotlin.collections.forEach

sealed class Screen {
    object Home : Screen()
    object Favorites : Screen()
    object Social : Screen()
    object Messages : Screen()
    object Profile : Screen()
    object Settings : Screen()
    object Upload : Screen()
    object MapView : Screen()
    data class Edit(val photo: TravelPhoto) : Screen()
    data class Detail(val photo: TravelPhoto, val fromMap: Boolean = false) : Screen() // Ajout de fromMap
    data class Chat(val otherUser: UserProfile) : Screen()
    data class GroupChat(val group: ChatGroup) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sharingPhoto by remember { mutableStateOf<TravelPhoto?>(null) }
    val auth = FirebaseAuth.getInstance()
    val isAnonymous = auth.currentUser?.isAnonymous ?: true

    val notificationRepository = remember { NotificationRepository() }
    val userRepository = remember { UserRepository() }
    
    LaunchedEffect(auth.currentUser?.uid) {
        val userId = auth.currentUser?.uid
        if (userId != null && !isAnonymous) {
            val userProfile = userRepository.getUserProfile(userId)
            notificationRepository.getNotificationsFlow(userId).collectLatest { notifications ->
                notifications.forEach { notif ->
                    val shouldNotify = when(notif.type) {
                        "MESSAGE" -> userProfile?.notifyMessages ?: true
                        "LIKE" -> userProfile?.notifyLikes ?: true
                        "FRIEND_REQUEST" -> userProfile?.notifyFriendRequests ?: true
                        "COMMENT" -> userProfile?.notifyComments ?: true
                        else -> true
                    }
                    
                    if (shouldNotify) {
                        showLocalNotification(context, notif.title, notif.message)
                    }
                    notificationRepository.markAsRead(notif.id)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Gestion du bouton retour intelligente
    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = when (val screen = currentScreen) {
            is Screen.Detail -> if (screen.fromMap) Screen.MapView else Screen.Home
            is Screen.Chat -> Screen.Messages
            is Screen.GroupChat -> Screen.Messages
            is Screen.Upload -> Screen.Home
            is Screen.Edit -> Screen.Home
            Screen.Favorites -> Screen.Home
            Screen.Social -> Screen.Home
            Screen.Messages -> Screen.Home
            Screen.Profile -> Screen.Home
            Screen.Settings -> Screen.Profile
            Screen.MapView -> Screen.Home
            else -> Screen.Home
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Accueil") },
                selected = currentScreen is Screen.Home,
                onClick = { currentScreen = Screen.Home }
            )
            item(
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                label = { Text("Favoris") },
                selected = currentScreen is Screen.Favorites,
                onClick = { currentScreen = Screen.Favorites }
            )
            item(
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                label = { Text("Messages") },
                selected = currentScreen is Screen.Messages || currentScreen is Screen.Chat || currentScreen is Screen.GroupChat,
                onClick = { currentScreen = Screen.Messages }
            )
            item(
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                label = { Text("Social") },
                selected = currentScreen is Screen.Social,
                onClick = { currentScreen = Screen.Social }
            )
            item(
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text("Profil") },
                selected = currentScreen is Screen.Profile || currentScreen is Screen.Settings,
                onClick = { currentScreen = Screen.Profile }
            )
        }
    ) {
        Scaffold(
            topBar = {
                if (currentScreen !is Screen.Detail && currentScreen !is Screen.Edit && 
                    currentScreen !is Screen.Chat && currentScreen !is Screen.GroupChat &&
                    currentScreen !is Screen.Settings && currentScreen !is Screen.MapView) {
                    TopAppBar(
                        title = { Text("TravelWow") },
                        actions = {
                            if (currentScreen is Screen.Home) {
                                IconButton(onClick = { currentScreen = Screen.MapView }) {
                                    Icon(Icons.Default.Map, contentDescription = "Carte")
                                }
                            }
                            IconButton(onClick = onLogout) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isAnonymous && currentScreen is Screen.Home) {
                    FloatingActionButton(onClick = { currentScreen = Screen.Upload }) {
                        Icon(Icons.Default.Add, contentDescription = "Publier")
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (val screen = currentScreen) {
                    is Screen.Home -> FeedScreen(onPhotoClick = {
                        currentScreen = Screen.Detail(it, fromMap = false)
                    })
                    is Screen.Detail -> PhotoDetailScreen(
                        photo = screen.photo,
                        onBack = {
                            currentScreen = if (screen.fromMap) Screen.MapView else Screen.Home
                        },
                        onEdit = { currentScreen = Screen.Edit(it) },
                        onDeleted = { currentScreen = Screen.Home },
                        onShare = { sharingPhoto = it }
                    )
                    is Screen.Edit -> UploadScreen(
                        editingPhoto = screen.photo,
                        onSuccess = { currentScreen = Screen.Home })
                    is Screen.Upload -> UploadScreen(onSuccess = { currentScreen = Screen.Home })
                    is Screen.Messages -> ChatListScreen(
                        onChatClick = { currentScreen = Screen.Chat(it) },
                        onGroupChatClick = { currentScreen = Screen.GroupChat(it) }
                    )
                    is Screen.Chat -> ChatScreen(
                        targetId = screen.otherUser.uid,
                        targetName = screen.otherUser.username,
                        isGroup = false,
                        onBack = { currentScreen = Screen.Messages },
                        onPhotoClick = { currentScreen = Screen.Detail(it, fromMap = false) }
                    )
                    is Screen.GroupChat -> ChatScreen(
                        targetId = screen.group.id,
                        targetName = screen.group.name,
                        isGroup = true,
                        onBack = { currentScreen = Screen.Messages },
                        onPhotoClick = { currentScreen = Screen.Detail(it, fromMap = false) }
                    )
                    is Screen.Social -> SocialScreen(onChatClick = {
                        currentScreen = Screen.Chat(it)
                    })
                    is Screen.Favorites -> FavoritesScreen(onPhotoClick = {
                        currentScreen = Screen.Detail(it, fromMap = false)
                    })
                    is Screen.Profile -> {
                        if (isAnonymous) {
                            PlaceholderScreen("Connectez-vous pour voir votre profil")
                        } else {
                            ProfileScreen(
                                onPhotoClick = {
                                    currentScreen = Screen.Detail(it, fromMap = false)
                                },
                                onSettingsClick = { currentScreen = Screen.Settings }
                            )
                        }
                    }
                    is Screen.Settings -> SettingsScreen(onBack = {
                        currentScreen = Screen.Profile
                    })
                    is Screen.MapView -> MapScreen(
                        onBack = { currentScreen = Screen.Home },
                        onPhotoClick = {
                            currentScreen = Screen.Detail(it, fromMap = true)
                        } // Passage de fromMap = true
                    )
                }

                sharingPhoto?.let { photo ->
                    SharePhotoDialog(
                        photo = photo,
                        onDismiss = { sharingPhoto = null },
                        onSent = { sharingPhoto = null }
                    )
                }
            }
        }
    }
}

private fun showLocalNotification(context: Context, title: String, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "travelwow_local_notifs"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Alertes TravelWow", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

@Composable
fun SharePhotoDialog(
    photo: TravelPhoto,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
    socialViewModel: SocialViewModel = viewModel(),
    chatListViewModel: ChatListViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val friends by socialViewModel.friends.collectAsState()
    val groups by chatListViewModel.groups.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text("Amis :", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.weight(1f, false)) {
                    items(friends) { friend ->
                        ListItem(
                            headlineContent = { Text("@${friend.username}") },
                            modifier = Modifier.clickable {
                                chatViewModel.sendMessage(friend.uid, "A partagé un voyage", photo.id, isGroup = false)
                                onSent()
                            }
                        )
                    }
                }
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Groupes :", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.weight(1f, false)) {
                        items(groups) { group ->
                            ListItem(
                                headlineContent = { Text(group.name) },
                                modifier = Modifier.clickable {
                                    chatViewModel.sendMessage(group.id, "A partagé un voyage", photo.id, isGroup = true)
                                    onSent()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
