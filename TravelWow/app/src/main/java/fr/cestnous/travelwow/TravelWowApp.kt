package fr.cestnous.travelwow

import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseUser as AuthUser
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.callback.ErrorInfo
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

suspend fun uploadToCloudinary(uri: String, context: android.content.Context): String = suspendCancellableCoroutine { continuation ->
    MediaManager.get().upload(uri.toUri())
        .option("upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET)
        .option("unsigned", true)
        .callback(object : UploadCallback {
            override fun onStart(requestId: String) {}
            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                val url = resultData["secure_url"] as? String ?: ""
                continuation.resume(url)
            }
            override fun onError(requestId: String, error: ErrorInfo) {
                continuation.resumeWithException(Exception(error.description))
            }
            override fun onReschedule(requestId: String, error: ErrorInfo) {}
        })
        .dispatch(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(
    user: AuthUser,
    onLogout: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    
    var userProfile by remember { mutableStateOf<FirebaseUser?>(null) }
    var userPostCount by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { Firebase.firestore }

    // Load user profile and post count from Firestore
    LaunchedEffect(user.uid) {
        try {
            // Load Profile
            val doc = db.collection("travelpath").document(user.uid).get().await()
            if (doc.exists()) {
                userProfile = doc.toObject(FirebaseUser::class.java)
            } else if (user.email != null) {
                // Initialize profile if it doesn't exist
                val initialUsername = user.displayName?.takeIf { it.isNotBlank() } 
                    ?: user.email!!.substringBefore("@")
                val initialProfile = FirebaseUser(
                    id = user.uid,
                    username = initialUsername,
                    email = user.email!!,
                )
                db.collection("travelpath").document(user.uid).set(initialProfile).await()
                userProfile = initialProfile
            }

            // Load Post Count
            val countSnapshot = db.collection("travelpath_posts")
                .whereEqualTo("authorId", user.uid)
                .get()
                .await()
            userPostCount = countSnapshot.size()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var focusedPostForMap by remember { mutableStateOf<FirebasePost?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    
    // Create Post State
    var showCreatePost by remember { mutableStateOf(false) }
    var showAddStep by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postLocation by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    var isSavingPost by remember { mutableStateOf(false) }
    var showPostSuccessDialog by remember { mutableStateOf(false) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Déconnexion") },
            text = { Text("Voulez-vous vraiment vous déconnecter de votre compte TravelWow ? Vos données resteront synchronisées sur Firebase.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // State for AddStepScreen
    var currentStepName by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(com.google.android.gms.maps.model.LatLng(43.6107, 3.8767)) }
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    // Auto-expand when bottom sheet is shown
    LaunchedEffect(showBottomSheet) {
        if (showBottomSheet) {
            sheetState.expand()
        }
    }

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
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
                if (!showSettings && !showEditProfile) {
                        when (currentDestination) {
                        AppDestinations.HOME -> SearchTopBar(
                            modifier = Modifier,
                            onAddClick = { showCreatePost = true },
                            onResetPost = {
                                // Reset and close
                                showCreatePost = false
                                postTitle = ""
                                postLocation = ""
                                postDescription = ""
                                postSteps = emptyList()
                            },
                            isAdding = showCreatePost,
                            isAddingStep = showAddStep,
                            onBackStepClick = { 
                                showAddStep = false
                                // Reset step state when going back
                                currentStepName = ""
                                currentStepImages = emptyList()
                            },
                            onConfirmStepClick = {
                                postSteps = postSteps + TravelStep(
                                    name = currentStepName.ifBlank { "Étape sans nom" },
                                    latitude = currentStepLocation.latitude,
                                    longitude = currentStepLocation.longitude,
                                    images = currentStepImages
                                )
                                showAddStep = false
                                // Reset step state
                                currentStepName = ""
                                currentStepImages = emptyList()
                            },
                            canConfirmStep = currentStepName.isNotBlank(),
                            canShare = postTitle.isNotBlank() && postSteps.isNotEmpty() && !isSavingPost && !showAddStep,
                            onShareClick = {
                                isSavingPost = true
                                coroutineScope.launch {
                                    try {
                                        val firebaseSteps = postSteps.mapIndexed { index, step ->
                                            val stepImageUrls = step.images.mapIndexed { imgIndex, uri ->
                                                if (uri.startsWith("content://") || uri.startsWith("file://")) {
                                                    Log.d("TravelWowApp", "Uploading step image $imgIndex to Cloudinary...")
                                                    try {
                                                        val downloadUrl = uploadToCloudinary(uri, context)
                                                        Log.d("TravelWowApp", "Step image $imgIndex uploaded: $downloadUrl")
                                                        downloadUrl
                                                    } catch (e: Exception) {
                                                        Log.e("TravelWowApp", "Failed to upload to Cloudinary", e)
                                                        throw e
                                                    }
                                                } else {
                                                    uri
                                                }
                                            }
                                            FirebaseStep(
                                                id = step.id,
                                                name = step.name,
                                                latitude = step.latitude,
                                                longitude = step.longitude,
                                                imageUrls = stepImageUrls,
                                                order = index
                                            )
                                        }

                                        val postRef = db.collection("travelpath_posts").document()
                                        val postId = postRef.id

                                        val post = FirebasePost(
                                            id = postId,
                                            authorId = user.uid,
                                            authorName = userProfile?.username ?: "Utilisateur inconnu",
                                            authorPhotoUrl = userProfile?.photoUrl,
                                            title = postTitle,
                                            locationName = firebaseSteps.firstOrNull()!!.name,
                                            description = postDescription,
                                            mainImageUrl = firebaseSteps.firstOrNull()!!.imageUrls.firstOrNull(),
                                            latitude = firebaseSteps.firstOrNull()!!.latitude,
                                            longitude = firebaseSteps.firstOrNull()!!.longitude,
                                            distanceKm = 0.0,
                                            durationMinutes = 0,
                                            steps = firebaseSteps,
                                            tags = List(0) { "" },
                                        )
                                        
                                        postRef.set(post).await()

                                        // Save steps to sub-collection
                                        val stepsCollection = postRef.collection("steps")
                                        firebaseSteps.forEach { firebaseStep ->
                                            stepsCollection.document(firebaseStep.id).set(firebaseStep).await()
                                        }
                                        
                                        showPostSuccessDialog = true
                                        showCreatePost = false
                                        postTitle = ""
                                        postLocation = ""
                                        postDescription = ""
                                        postSteps = emptyList()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSavingPost = false
                                    }
                                }
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
            if (showEditProfile) {
                EditProfileScreen(
                    userId = user.uid,
                    currentUsername = userProfile?.username ?: "Utilisateur inconnu",
                    currentBio = userProfile?.bio ?: "",
                    currentPhotoUri = userProfile?.photoUrl,
                    onBack = { showEditProfile = false },
                    onSave = { newName, newBio, newPhotoUri ->
                        coroutineScope.launch {
                            try {
                                Log.d("TravelWowApp", "Starting profile save to Cloudinary. PhotoUri: $newPhotoUri")
                                var finalPhotoUrl = newPhotoUri
                                
                                // Upload image to Cloudinary if it's a local URI
                                if (newPhotoUri != null && (newPhotoUri.startsWith("content://") || newPhotoUri.startsWith("file://"))) {
                                    finalPhotoUrl = uploadToCloudinary(newPhotoUri, context)
                                    Log.d("TravelWowApp", "Profile pic upload successful: $finalPhotoUrl")
                                }

                                val updates = mutableMapOf<String, Any>(
                                    "username" to newName,
                                    "bio" to newBio
                                )
                                finalPhotoUrl?.let { updates["photoUrl"] = it }
                                
                                db.collection("travelpath").document(user.uid)
                                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                    .await()
                                
                                Log.d("TravelWowApp", "Firestore profile updated")
                                
                                userProfile = userProfile?.copy(
                                    username = newName,
                                    bio = newBio,
                                    photoUrl = finalPhotoUrl
                                )
                                showEditProfile = false
                            } catch (e: Exception) {
                                Log.e("TravelWowApp", "Error saving profile: ${e.message}", e)
                                // In a real app, show a Snackbar here
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showSettings) {
                SettingsScreen(
                    settings = userProfile?.settings ?: FirebaseUserSettings(),
                    userEmail = user.email ?: "Utilisateur",
                    onBack = { showSettings = false },
                    onLogout = { 
                        showSettings = false
                        onLogout() 
                    },
                    onSave = { newSettings ->
                        coroutineScope.launch {
                            try {
                                db.collection("travelpath").document(user.uid)
                                    .update("settings", newSettings)
                                    .await()
                                userProfile = userProfile?.copy(settings = newSettings)
                                showSettings = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentDestination) {
                                AppDestinations.HOME -> {
                                    if (showAddStep) {
                                        AddStepScreen(
                                            stepName = currentStepName,
                                            onStepNameChange = { currentStepName = it },
                                            stepImages = currentStepImages,
                                            onStepImagesChange = { currentStepImages = it },
                                            onLocationSelected = { currentStepLocation = it },
                                            lastStepLocation = postSteps.lastOrNull()?.let { LatLng(it.latitude, it.longitude) },
                                            modifier = Modifier
                                        )
                                    } else if (showCreatePost) {
                                        CreatePostContent(
                                            title = postTitle,
                                            onTitleChange = { postTitle = it },
                                            location = postLocation,
                                            onLocationChange = { postLocation = it },
                                            description = postDescription,
                                            onDescriptionChange = { postDescription = it },
                                            steps = postSteps,
                                            onAddStepClick = { showAddStep = true },
                                            onRemoveStep = { step -> postSteps = postSteps.filter { it.id != step.id } },
                                            onStepsChange = { postSteps = it },
                                            modifier = Modifier
                                        )
                                    } else {
                                        SearchScreen(
                                            onPostClick = { post ->
                                                selectedPost = post
                                                focusedPostForMap = post
                                                showBottomSheet = true
                                            },
                                            viewMode = galleryViewMode,
                                            modifier = Modifier,
                                            focusedPost = focusedPostForMap,
                                            onFocusedPostChange = { focusedPostForMap = it }
                                        )
                                    }
                                }
                                AppDestinations.FAVORITES -> Box(modifier = Modifier.fillMaxSize()) {
                                    PostsGallery(
                                        onPostClick = { post ->
                                            selectedPost = post
                                            focusedPostForMap = post
                                            showBottomSheet = true
                                        },
                                        viewMode = galleryViewMode,
                                        modifier = Modifier.fillMaxSize(),
                                        favoritesUserId = user.uid,
                                        focusedPost = focusedPostForMap,
                                        onFocusedPostChange = { focusedPostForMap = it }
                                    )
                                }
                                AppDestinations.PROFILE -> Box(modifier = Modifier.fillMaxSize()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        ProfileHeader(
                                            username = userProfile?.username ?: "Utilisateur inconnu",
                                            bio = userProfile?.bio ?: "",
                                            photoUri = userProfile?.photoUrl,
                                            postsCount = userPostCount,
                                            followersCount = userProfile?.followersCount ?: 0,
                                            followingCount = userProfile?.followingCount ?: 0,
                                            viewMode = galleryViewMode,
                                            onViewModeChange = { galleryViewMode = it },
                                            onSettingsClick = { showSettings = true },
                                            onLogoutClick = { showLogoutDialog = true },
                                            onEditProfileClick = { showEditProfile = true }
                                        )

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                                        PostsGallery(
                                            onPostClick = { post ->
                                                selectedPost = post
                                                focusedPostForMap = post
                                                showBottomSheet = true
                                            },
                                            viewMode = galleryViewMode,
                                            modifier = Modifier.weight(1f),
                                            userIdFilter = user.uid,
                                            focusedPost = focusedPostForMap,
                                            onFocusedPostChange = { focusedPostForMap = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showBottomSheet) {
                DetailsBottomSheet(
                    post = selectedPost,
                    onDismissRequest = {
                        showBottomSheet = false
                        selectedPost = null
                        focusedPostForMap = null
                    },
                    sheetState = sheetState,
                    currentUserProfile = userProfile
                )
            }

            if (showPostSuccessDialog) {
                PostSuccessDialog(onDismiss = { showPostSuccessDialog = false })
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

