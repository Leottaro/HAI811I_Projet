package fr.cestnous.travelwow

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fr.cestnous.travelwow.travelPath.data.FirebasePost
import fr.cestnous.travelwow.travelPath.ParcoursScreen
import fr.cestnous.travelwow.travelPath.ui.DetailsSheetContent
import fr.cestnous.travelwow.travelShare.ui.feed.FeedScreen
import fr.cestnous.travelwow.travelShare.ui.favorites.FavoritesScreen as ShareFavorites
import fr.cestnous.travelwow.travelShare.ui.chat.ChatListScreen
import fr.cestnous.travelwow.travelShare.ui.chat.ChatScreen
import fr.cestnous.travelwow.travelShare.ui.profile.ProfileScreen as ShareProfile
import fr.cestnous.travelwow.travelShare.ui.profile.SettingsScreen as ShareSettings
import fr.cestnous.travelwow.travelShare.ui.upload.UploadScreen
import fr.cestnous.travelwow.travelShare.ui.map.MapScreen as ShareMap
import fr.cestnous.travelwow.travelShare.ui.detail.PhotoDetailScreen
import fr.cestnous.travelwow.travelShare.ui.social.SocialScreen
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.ui.components.PlaceholderScreen

enum class AppMode { SHARE, PATH }

sealed class MainDestination(val label: String, val icon: @Composable () -> Unit) {
    data object Feed : MainDestination("Accueil", { Icon(Icons.Default.Home, null) })
    data object Favorites : MainDestination("Favoris", { Icon(Icons.Default.Favorite, null) })
    data object Messages : MainDestination("Messages", { Icon(Icons.AutoMirrored.Outlined.Message, null) })
    data object Social : MainDestination("Social", { Icon(Icons.Default.People, null) })
    data object Profile : MainDestination("Profil", { Icon(Icons.Default.Person, null) })

    companion object {
        fun values() = listOf(Feed, Favorites, Messages, Social, Profile)
    }
}

sealed class SubScreen {
    data object None : SubScreen()
    data class PhotoDetail(val photo: TravelPhoto, val fromMap: Boolean = false) : SubScreen()
    data object PhotoUpload : SubScreen()
    data class PhotoEdit(val photo: TravelPhoto) : SubScreen()
    data class Chat(val user: UserProfile) : SubScreen()
    data class GroupChat(val group: ChatGroup) : SubScreen()
    data object PhotoMap : SubScreen()
    data object Settings : SubScreen()
    data class PostDetail(val post: FirebasePost) : SubScreen() // Added for routes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(
    user: FirebaseUser,
    onLogout: () -> Unit
) {
    var appMode by remember { mutableStateOf(AppMode.SHARE) }
    var currentDestination by remember { mutableStateOf<MainDestination>(MainDestination.Feed) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    val isAnonymous = user.isAnonymous

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = subScreen !is SubScreen.None || appMode == AppMode.PATH) {
        if (subScreen !is SubScreen.None) {
            subScreen = SubScreen.None
        } else if (appMode == AppMode.PATH) {
            appMode = AppMode.SHARE
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MainDestination.values().forEach { dest ->
                val isRestricted = isAnonymous && (dest == MainDestination.Messages || dest == MainDestination.Social || dest == MainDestination.Profile)

                item(
                    icon = {
                        CompositionLocalProvider(
                            LocalContentColor provides if (isRestricted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else LocalContentColor.current
                        ) {
                            dest.icon()
                        }
                    },
                    label = {
                        Text(
                            text = dest.label,
                            color = if (isRestricted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
                        )
                    },
                    selected = currentDestination == dest,
                    onClick = { 
                        if (!isRestricted) {
                            currentDestination = dest
                            subScreen = SubScreen.None
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (subScreen) {
                is SubScreen.PhotoDetail -> PhotoDetailScreen(
                    photo = (subScreen as SubScreen.PhotoDetail).photo,
                    onBack = { subScreen = SubScreen.None },
                    onEdit = { subScreen = SubScreen.PhotoEdit(it) },
                    onDeleted = { subScreen = SubScreen.None },
                    onShare = { /* Logic for sharing */ }
                )
                SubScreen.PhotoUpload -> UploadScreen(onSuccess = { subScreen = SubScreen.None })
                is SubScreen.PhotoEdit -> UploadScreen(
                    editingPhoto = (subScreen as SubScreen.PhotoEdit).photo,
                    onSuccess = { subScreen = SubScreen.None }
                )
                is SubScreen.Chat -> ChatScreen(
                    targetId = (subScreen as SubScreen.Chat).user.uid,
                    targetName = (subScreen as SubScreen.Chat).user.username,
                    isGroup = false,
                    onBack = { subScreen = SubScreen.None },
                    onPhotoClick = { subScreen = SubScreen.PhotoDetail(it) },
                    onPostClick = { subScreen = SubScreen.PostDetail(it) } // Handled route click
                )
                is SubScreen.GroupChat -> ChatScreen(
                    targetId = (subScreen as SubScreen.GroupChat).group.id,
                    targetName = (subScreen as SubScreen.GroupChat).group.name,
                    isGroup = true,
                    onBack = { subScreen = SubScreen.None },
                    onPhotoClick = { subScreen = SubScreen.PhotoDetail(it) },
                    onPostClick = { subScreen = SubScreen.PostDetail(it) } // Handled route click
                )
                SubScreen.PhotoMap -> Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Carte des voyages") },
                            navigationIcon = {
                                IconButton(onClick = { subScreen = SubScreen.None }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        )
                    }
                ) { p ->
                    Box(modifier = Modifier.padding(p)) {
                        ShareMap(
                            onBack = { subScreen = SubScreen.None },
                            onPhotoClick = { subScreen = SubScreen.PhotoDetail(it, true) }
                        )
                    }
                }
                SubScreen.Settings -> ShareSettings(onBack = { subScreen = SubScreen.None })
                is SubScreen.PostDetail -> {
                    val sheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.Expanded,
                        skipHiddenState = false
                    )
                    val scope = rememberCoroutineScope()

                    // Sync subScreen with sheetState
                    LaunchedEffect(sheetState.currentValue) {
                        if (sheetState.currentValue == SheetValue.Hidden) {
                            subScreen = SubScreen.None
                        }
                    }

                    DetailsSheetContent(
                        post = (subScreen as SubScreen.PostDetail).post,
                        onDismissRequest = {
                            scope.launch {
                                sheetState.hide()
                                subScreen = SubScreen.None
                            }
                        },
                        sheetState = sheetState
                    )
                }
                SubScreen.None -> {
                    val isPathEligible = currentDestination == MainDestination.Feed ||
                                       currentDestination == MainDestination.Favorites

                    if (appMode == AppMode.PATH && isPathEligible) {
                        ParcoursScreen(
                            user = user,
                            isFavoriteTab = currentDestination == MainDestination.Favorites,
                            onLogout = onLogout,
                            onBackToShare = { appMode = AppMode.SHARE }
                        )
                    } else {
                        when (currentDestination) {
                            MainDestination.Feed -> Scaffold(
                                topBar = {
                                    CenterAlignedTopAppBar(
                                        title = { Text("TravelWow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                                        navigationIcon = {
                                            IconButton(onClick = { appMode = AppMode.PATH }) {
                                                Icon(Icons.Default.Explore, contentDescription = "Mode Parcours", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = { subScreen = SubScreen.PhotoMap }) {
                                                Icon(Icons.Default.Map, "Carte")
                                            }
                                            IconButton(onClick = onLogout) {
                                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Déconnexion", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                },
                                floatingActionButton = {
                                    if (!isAnonymous) {
                                        FloatingActionButton(onClick = { subScreen = SubScreen.PhotoUpload }) {
                                            Icon(Icons.Default.Add, "Publier")
                                        }
                                    }
                                }
                            ) { p ->
                                Box(modifier = Modifier.padding(p)) {
                                    FeedScreen(onPhotoClick = { subScreen = SubScreen.PhotoDetail(it) })
                                }
                            }
                            MainDestination.Favorites -> Scaffold(
                                topBar = {
                                    CenterAlignedTopAppBar(
                                        title = { Text("Mes Favoris", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                                        navigationIcon = {
                                            IconButton(onClick = { appMode = AppMode.PATH }) {
                                                Icon(Icons.Default.Explore, contentDescription = "Mode Parcours", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = onLogout) {
                                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Déconnexion", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                }
                            ) { p ->
                                Box(modifier = Modifier.padding(p)) {
                                    ShareFavorites(onPhotoClick = { subScreen = SubScreen.PhotoDetail(it) })
                                }
                            }
                            MainDestination.Messages -> ChatListScreen(
                                onChatClick = { subScreen = SubScreen.Chat(it) },
                                onGroupChatClick = { subScreen = SubScreen.GroupChat(it) },
                                onPostClick = { subScreen = SubScreen.PostDetail(it) } // Added
                            )
                            MainDestination.Social -> SocialScreen(
                                onChatClick = { subScreen = SubScreen.Chat(it) },
                                onPostClick = { subScreen = SubScreen.PostDetail(it) } // Added
                            )
                            MainDestination.Profile -> {
                                if (isAnonymous) {
                                    PlaceholderScreen("Connectez-vous pour voir votre profil")
                                } else {
                                    ShareProfile(
                                        onPhotoClick = { subScreen = SubScreen.PhotoDetail(it) },
                                        onSettingsClick = { subScreen = SubScreen.Settings },
                                        onPostClick = { subScreen = SubScreen.PostDetail(it) } // Added
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
