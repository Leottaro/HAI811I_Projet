package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(
    user: FirebaseUser,
    onLogout: () -> Unit
) {
    val username = remember(user) {
        user.displayName?.takeIf { it.isNotBlank() } 
            ?: user.email?.substringBefore("@") 
            ?: "Utilisateur"
    }
    
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var userBio by rememberSaveable { mutableStateOf("Explorateur de sentiers et passionné de randonnée. 🏔️🥾\nPartage mes meilleurs parcours autour du monde.") }
    var customUsername by rememberSaveable { mutableStateOf("") }
    var profilePhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    
    val effectiveUsername = customUsername.ifBlank { username }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    
    // Create Post State
    var showCreatePost by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postLocation by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    NavigationSuiteScaffold(
        modifier = Modifier.padding(bottom = 16.dp),
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination && !showSettings,
                    onClick = {
                        currentDestination = it
                        showSettings = false
                        showEditProfile = false
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (showSettings || showEditProfile) {
                    // TopAppBar is handled inside EditProfileScreen or here for Settings
                    if (showSettings) {
                        TopAppBar(
                            title = { Text("Paramètres") },
                            navigationIcon = {
                                IconButton(onClick = { showSettings = false }) {
                                    Icon(painterResource(R.drawable.ic_return), contentDescription = "Retour")
                                }
                            }
                        )
                    }
                } else {
                    when (currentDestination) {
                        AppDestinations.HOME -> SearchTopBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onAddClick = { 
                                if (showCreatePost) {
                                    // Reset and close
                                    showCreatePost = false
                                    postTitle = ""
                                    postLocation = ""
                                    postDescription = ""
                                    postSteps = emptyList()
                                } else {
                                    showCreatePost = true 
                                }
                            },
                            isAdding = showCreatePost,
                            canShare = postTitle.isNotBlank() && postDescription.isNotBlank() && postSteps.isNotEmpty(),
                            onShareClick = {
                                // TODO: Firebase save logic
                                showCreatePost = false
                                postTitle = ""
                                postLocation = ""
                                postDescription = ""
                                postSteps = emptyList()
                            },
                            viewMode = galleryViewMode,
                            onViewModeChange = { galleryViewMode = it }
                        )
                        AppDestinations.FAVORITES -> TopAppBar(
                            title = { Text(currentDestination.label) },
                            actions = {
                                IconButton(onClick = {
                                    val newMode = if (galleryViewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                                    galleryViewMode = newMode
                                }) {
                                    Icon(
                                        painter = painterResource(if (galleryViewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel),
                                        contentDescription = "Changer de vue"
                                    )
                                }
                            }
                        )
                        AppDestinations.PROFILE -> { /* No TopAppBar for Profile as requested */ }
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showSettings) {
                        SettingsScreen(modifier = Modifier.padding(innerPadding))
                    } else if (showEditProfile) {
                        EditProfileScreen(
                            currentUsername = effectiveUsername,
                            currentBio = userBio,
                            currentPhotoUri = profilePhotoUri,
                            onBack = { showEditProfile = false },
                            onSave = { newName, newBio, newPhotoUri ->
                                customUsername = newName
                                userBio = newBio
                                profilePhotoUri = newPhotoUri
                                showEditProfile = false
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        when (currentDestination) {
                            AppDestinations.HOME -> {
                                if (showCreatePost) {
                                    CreatePostContent(
                                        title = postTitle,
                                        onTitleChange = { postTitle = it },
                                        location = postLocation,
                                        onLocationChange = { postLocation = it },
                                        description = postDescription,
                                        onDescriptionChange = { postDescription = it },
                                        steps = postSteps,
                                        onAddStep = { postSteps = postSteps + it },
                                        onRemoveStep = { step -> postSteps = postSteps.filter { it.id != step.id } },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                } else {
                                    SearchScreen(
                                        selectedItem = if (showBottomSheet) selectedItem else null,
                                        onItemClick = { index ->
                                            selectedItem = index
                                            showBottomSheet = true
                                        },
                                        viewMode = galleryViewMode,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                            AppDestinations.FAVORITES -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                PostsGallery(
                                    selectedItem = if (showBottomSheet) selectedItem else null,
                                    onItemClick = { index ->
                                        selectedItem = index
                                        showBottomSheet = true
                                    },
                                    viewMode = galleryViewMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AppDestinations.PROFILE -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    ProfileHeader(
                                        username = effectiveUsername,
                                        bio = userBio,
                                        photoUri = profilePhotoUri,
                                        viewMode = galleryViewMode,
                                        onViewModeChange = { galleryViewMode = it },
                                        onSettingsClick = { showSettings = true },
                                        onLogoutClick = onLogout,
                                        onEditProfileClick = { showEditProfile = true }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                                    PostsGallery(
                                        selectedItem = if (showBottomSheet) selectedItem else null,
                                        onItemClick = { index ->
                                            selectedItem = index
                                            showBottomSheet = true
                                        },
                                        viewMode = galleryViewMode,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (showBottomSheet) {
                            DetailsBottomSheet(
                                selectedItem = selectedItem,
                                onDismissRequest = { showBottomSheet = false },
                                sheetState = sheetState
                            )
                        }
                    }
                }

                if (showBottomSheet) {
                    DetailsBottomSheet(
                        selectedItem = selectedItem,
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Accueil", R.drawable.ic_home),
    FAVORITES("Favoris", R.drawable.ic_favorite),
    PROFILE("Profil", R.drawable.ic_account_box),
}

@Composable
fun ProfileHeader(
    username: String,
    viewMode: GalleryViewMode,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    bio: String,
    photoUri: String? = null,
    postsCount: Int = 30,
    followersCount: Int = 128,
    followingCount: Int = 94
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Photo de profil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_account_box),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Stats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(label = "Publications", value = postsCount.toString())
                ProfileStat(label = "Abonnés", value = followersCount.toString())
                ProfileStat(label = "Abonnements", value = followingCount.toString())
            }
        }

        // Username and Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = {
                        val newMode = if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                        onViewModeChange(newMode)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(if (viewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel),
                            contentDescription = "Changer de vue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Surface(
                    onClick = onSettingsClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Paramètres",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = onLogoutClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_return),
                            contentDescription = "Déconnexion",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bio
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        
        // Edit Profile Button
        Button(
            onClick = onEditProfileClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("Modifier le profil", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Préférences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsToggleItem(
                        title = "Publications des abonnés",
                        description = "Nouveaux parcours de vos amis",
                        initialValue = true
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleItem(
                        title = "Likes sur vos posts",
                        description = "Alertes pour les nouveaux likes",
                        initialValue = true
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleItem(
                        title = "Commentaires",
                        description = "Notifications pour les messages",
                        initialValue = true
                    )
                }
            }
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Compte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    SettingsNavigationItem(title = "Confidentialité")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationItem(title = "Sécurité")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationItem(title = "Aide et assistance")
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "TravelWow v1.0.0",
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun SettingsToggleItem(title: String, description: String, initialValue: Boolean) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(
            checked = checked, 
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun SettingsNavigationItem(title: String) {
    Surface(
        onClick = { /* TODO */ },
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(
                painter = painterResource(R.drawable.ic_return), // Reuse return icon as chevron
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}