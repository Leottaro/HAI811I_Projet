package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(
    user: FirebaseUser,
    onLogout: () -> Unit
) {
    val username = user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "Utilisateur"
    
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
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
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
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
                        canShare = postTitle.isNotBlank() && postDescription.isNotBlank(),
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
        ) { innerPadding ->
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
                            selectedImages = emptyList(),
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
                            username = username,
                            viewMode = galleryViewMode,
                            onViewModeChange = { galleryViewMode = it },
                            onSettingsClick = onLogout
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
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun ProfileHeader(
    username: String,
    viewMode: GalleryViewMode,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onSettingsClick: () -> Unit,
    bio: String = "Explorateur de sentiers et passionné de randonnée. 🏔️🥾\nPartage mes meilleurs parcours autour du monde.",
    postsCount: Int = 30,
    followersCount: Int = 128,
    followingCount: Int = 94
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Profile Picture
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_account_box),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Stats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(label = "Posts", value = postsCount.toString())
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val newMode = if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                    onViewModeChange(newMode)
                }) {
                    Icon(
                        painter = painterResource(if (viewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel),
                        contentDescription = "Changer de vue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filters),
                        contentDescription = "Paramètres",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Bio
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )
        
        // Edit Profile Button
        OutlinedButton(
            onClick = { /* TODO */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text("Modifier le profil", style = MaterialTheme.typography.labelLarge)
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
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Bienvenue, $name!",
        modifier = modifier.padding(16.dp)
    )
}
