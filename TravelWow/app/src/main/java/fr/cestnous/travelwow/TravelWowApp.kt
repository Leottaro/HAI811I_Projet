package fr.cestnous.travelwow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.ui.feed.FeedScreen
import fr.cestnous.travelwow.ui.detail.PhotoDetailScreen
import fr.cestnous.travelwow.ui.upload.UploadScreen
import fr.cestnous.travelwow.ui.components.PlaceholderScreen
import fr.cestnous.travelwow.ui.profile.ProfileScreen

sealed class Screen {
    object Home : Screen()
    object Favorites : Screen()
    object Profile : Screen()
    object Upload : Screen()
    data class Edit(val photo: TravelPhoto) : Screen()
    data class Detail(val photo: TravelPhoto) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(onLogout: () -> Unit) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val isAnonymous = FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true

    // Gestion du bouton retour physique
    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = when (currentScreen) {
            is Screen.Detail -> Screen.Home
            is Screen.Upload -> Screen.Home
            is Screen.Edit -> Screen.Home
            Screen.Favorites -> Screen.Home
            Screen.Profile -> Screen.Home
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
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text("Profil") },
                selected = currentScreen is Screen.Profile,
                onClick = { currentScreen = Screen.Profile }
            )
        }
    ) {
        Scaffold(
            topBar = {
                if (currentScreen !is Screen.Detail && currentScreen !is Screen.Edit) {
                    TopAppBar(
                        title = { Text("TravelWow") },
                        actions = {
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
                    is Screen.Home -> FeedScreen(
                        onPhotoClick = { currentScreen = Screen.Detail(it) }
                    )
                    is Screen.Detail -> PhotoDetailScreen(
                        photo = screen.photo,
                        onBack = { currentScreen = Screen.Home },
                        onEdit = { currentScreen = Screen.Edit(it) },
                        onDeleted = { currentScreen = Screen.Home }
                    )
                    is Screen.Edit -> UploadScreen(
                        editingPhoto = screen.photo,
                        onSuccess = { currentScreen = Screen.Home }
                    )
                    is Screen.Upload -> UploadScreen(
                        onSuccess = { currentScreen = Screen.Home }
                    )
                    is Screen.Favorites -> PlaceholderScreen("Mes Favoris (Bientôt disponible)")
                    is Screen.Profile -> {
                        if (isAnonymous) {
                            PlaceholderScreen("Connectez-vous pour voir votre profil")
                        } else {
                            ProfileScreen(
                                onPhotoClick = { currentScreen = Screen.Detail(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
