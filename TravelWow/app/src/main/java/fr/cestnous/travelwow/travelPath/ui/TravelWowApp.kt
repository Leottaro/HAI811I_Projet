package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.content.Context
import android.util.Log
import kotlin.math.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FieldPath
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.callback.ErrorInfo
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.collections.get

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
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val localDb = remember { TravelWowDatabase.getDatabase(context) }
    val draftDao = remember { localDb.draftDao() }

    // Load user profile and post count from Firestore
    LaunchedEffect(user.uid) {
        try {
            // Get FCM Token
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("TravelWowApp", "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }
                val token = task.result
                Log.d("TravelWowApp", "FCM Token: $token")
                TravelWowMessagingService.updateTokenInFirestore(user.uid, token)
            })

            // Load Profile and listen for changes
            db.collection("users").document(user.uid).addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("TravelWowApp", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    userProfile = snapshot.toObject(FirebaseUser::class.java)
                } else if (userProfile == null && user.email != null) {
                    // Initialize profile if it doesn't exist (one-time)
                    val initialUsername = user.displayName?.takeIf { it.isNotBlank() } 
                        ?: user.email!!.substringBefore("@")
                    val initialProfile = FirebaseUser(
                        id = user.uid,
                        username = initialUsername,
                        email = user.email!!,
                    )
                    db.collection("users").document(user.uid).set(initialProfile)
                }
            }

            // Load Post Count
            val countSnapshot = db.collection("travelpath_posts")
                .whereEqualTo("authorId", user.uid)
                .get()
                .await()
            userPostCount = countSnapshot.size()

            // Listen for notifications (Option 1: Free alternative to Firebase Functions)
            db.collection("notifications")
                .whereEqualTo("recipientId", user.uid)
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("TravelWowApp", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { dc ->
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val notif = dc.document.toObject(FirebaseNotification::class.java)
                            
                            // Trigger local notification
                            TravelWowMessagingService.sendLocalNotification(
                                context,
                                notif.title,
                                notif.message
                            )
                            
                            // Mark as read immediately so it doesn't repeat
                            db.collection("notifications").document(dc.document.id)
                                .update("isRead", true)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var focusedPostForMap by remember { mutableStateOf<FirebasePost?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    
    // Search Filter State
    var postFilter by remember { mutableStateOf(PostFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val isFilterActive = postFilter.selectedCategories.isNotEmpty() || postFilter.minDistance > 0f || postFilter.maxDistance < 100f

    // Create Post State
    var showCreatePost by remember { mutableStateOf(false) }
    var showAddStep by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postLocation by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    var isSavingPost by remember { mutableStateOf(false) }
    var isSavingDraft by remember { mutableStateOf(false) }
    var showPostSuccessDialog by remember { mutableStateOf(false) }
    var showDraftSuccessDialog by remember { mutableStateOf(false) }
    var editingDraftId by remember { mutableStateOf<String?>(null) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Déconnexion") },
            text = { Text("Voulez-vous vraiment vous déconnecter de votre compte TravelWow ? Vos données resteront synchronisées sur Firebase.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            TravelWowDatabase.getDatabase(context).favoritePostDao().clearAll()
                            showLogoutDialog = false
                            onLogout()
                        } catch (e: Exception) {
                            Log.e("TravelWowApp", "Logout error", e)
                            onLogout() // Fallback
                        }
                    }
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
    var currentStepCategory by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    
    val closeBottomSheet = {
        showBottomSheet = false
        coroutineScope.launch {
            try {
                sheetState.partialExpand()
            } catch (e: Exception) {
                Log.e("TravelWowApp", "Error closing bottom sheet", e)
            }
            selectedPost = null
            focusedPostForMap = null
        }
        Unit
    }

    // Automatically deselect the post when the page changes
    LaunchedEffect(currentDestination) {
        closeBottomSheet()
    }

    // BackHandler to dismiss various states
    BackHandler(enabled = showBottomSheet || showCreatePost || showAddStep || showSettings || showEditProfile) {
        if (showAddStep) {
            showAddStep = false
            currentStepName = ""
            currentStepCategory = ""
            currentStepImages = emptyList()
        } else if (showCreatePost) {
            showCreatePost = false
            postTitle = ""
            postLocation = ""
            postDescription = ""
            postSteps = emptyList()
            editingDraftId = null
        } else if (showEditProfile) {
            showEditProfile = false
        } else if (showSettings) {
            showSettings = false
        } else if (showBottomSheet) {
            closeBottomSheet()
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
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (showBottomSheet) 140.dp else 0.dp,
            sheetDragHandle = { if (showBottomSheet) BottomSheetDefaults.DragHandle() },
            sheetSwipeEnabled = showBottomSheet,
            sheetContent = {
                DetailsSheetContent(
                    post = selectedPost,
                    onDismissRequest = closeBottomSheet,
                    sheetState = sheetState,
                    currentUserProfile = userProfile
                )
            },
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!showSettings && !showEditProfile) {
                        when (currentDestination) {
                        AppDestinations.HOME -> SearchTopBar(
                            modifier = Modifier,
                            onResetPost = {
                                // Reset and close
                                showCreatePost = false
                                postTitle = ""
                                postLocation = ""
                                postDescription = ""
                                postSteps = emptyList()
                                editingDraftId = null
                            },
                            isAdding = showCreatePost,
                            isAddingStep = showAddStep,
                            onBackStepClick = { 
                                showAddStep = false
                                // Reset step state when going back
                                currentStepName = ""
                                currentStepCategory = ""
                                currentStepImages = emptyList()
                            },
                            onConfirmStepClick = {
                                postSteps = postSteps + TravelStep(
                                    name = currentStepName.ifBlank { "Étape sans nom" },
                                    category = currentStepCategory,
                                    latitude = currentStepLocation.latitude,
                                    longitude = currentStepLocation.longitude,
                                    images = currentStepImages
                                )
                                showAddStep = false
                                // Reset step state
                                currentStepName = ""
                                currentStepCategory = ""
                                currentStepImages = emptyList()
                            },
                            canConfirmStep = currentStepName.isNotBlank(),
                            canShare = postTitle.isNotBlank() && postSteps.isNotEmpty() && !isSavingPost && !showAddStep,
                            onShareClick = {
                                // ... (rest of onShareClick remains same)
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
                                                category = step.category,
                                                latitude = step.latitude,
                                                longitude = step.longitude,
                                                imageUrls = stepImageUrls,
                                                order = index
                                            )
                                        }

                                        val postRef = db.collection("travelpath_posts").document()
                                        val postId = postRef.id

                                        val totalDistance = calculateTotalDistance(postSteps)
                                        val post = FirebasePost(
                                            id = postId,
                                            authorId = user.uid,
                                            title = postTitle,
                                            locationName = firebaseSteps.firstOrNull()!!.name,
                                            description = postDescription,
                                            mainImageUrl = firebaseSteps.flatMap { it.imageUrls }.firstOrNull(),
                                            latitude = firebaseSteps.firstOrNull()!!.latitude,
                                            longitude = firebaseSteps.firstOrNull()!!.longitude,
                                            distanceKm = (totalDistance * 10).roundToInt() / 10.0,
                                            durationMinutes = 0,
                                            steps = firebaseSteps,
                                            categories = firebaseSteps.map { it.category }.filter { it.isNotBlank() }.distinct(),
                                        )
                                        
                                        postRef.set(post).await()

                                        // Save steps to sub-collection
                                        val stepsCollection = postRef.collection("steps")
                                        firebaseSteps.forEach { firebaseStep ->
                                            Log.i("TRUC", firebaseStep.toString());
                                            stepsCollection.document(firebaseStep.id).set(firebaseStep).await()
                                        }

                                        // Notify all followers
                                        coroutineScope.launch {
                                            try {
                                                val followersSnapshot = db.collection("users")
                                                    .document(user.uid)
                                                    .collection("followers")
                                                    .get()
                                                    .await()

                                                val senderName = userProfile?.username ?: "Un voyageur"
                                                val followerIds = followersSnapshot.documents.map { it.id }

                                                if (followerIds.isNotEmpty()) {
                                                    // Process in chunks of 10 to check settings
                                                    followerIds.chunked(10).forEach { chunk ->
                                                        val profiles = db.collection("users")
                                                            .whereIn(FieldPath.documentId(), chunk)
                                                            .get()
                                                            .await()

                                                        profiles.documents.forEach { profileDoc ->
                                                            val profile = profileDoc.toObject(FirebaseUser::class.java)
                                                            if (profile?.settings?.followersPostsNotifications == true) {
                                                                val notification = FirebaseNotification(
                                                                    recipientId = profileDoc.id,
                                                                    senderId = user.uid,
                                                                    senderName = senderName,
                                                                    senderPhotoUrl = userProfile?.photoUrl,
                                                                    type = NotificationType.NEW_POST,
                                                                    targetId = postId,
                                                                    title = "Nouveau parcours !",
                                                                    message = "$senderName a publié un nouveau parcours : \"$postTitle\"."
                                                                )
                                                                db.collection("notifications").add(notification)
                                                            }
                                                        }
                                                    }
                                                }
                                                Log.d("TravelWowApp", "Processed notifications for ${followersSnapshot.size()} followers")
                                            } catch (e: Exception) {
                                                Log.e("TravelWowApp", "Error notifying followers", e)
                                            }
                                        }
                                        
                                        showPostSuccessDialog = true
                                        showCreatePost = false
                                        postTitle = ""
                                        postLocation = ""
                                        postDescription = ""
                                        postSteps = emptyList()

                                        // Delete draft if it was one
                                        editingDraftId?.let { draftId ->
                                            try {
                                                db.collection("users").document(user.uid)
                                                    .collection("drafts").document(draftId)
                                                    .delete()
                                                Log.d("TravelWowApp", "Draft $draftId deleted after publication")
                                            } catch (e: Exception) {
                                                Log.e("TravelWowApp", "Error deleting draft $draftId", e)
                                            }
                                            editingDraftId = null
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSavingPost = false
                                    }
                                }
                            },
                            viewMode = galleryViewMode,
                            onViewModeChange = { galleryViewMode = it },
                            isPostSelected = showBottomSheet,
                            onDeselect = closeBottomSheet,
                            onFilterClick = { showFilterSheet = true },
                            isFilterActive = isFilterActive,
                            onLogout = onLogout
                        )
                        AppDestinations.FAVORITES -> TopAppBar(
                            title = { Text(currentDestination.label) },
                            navigationIcon = {
                                if (showBottomSheet) {
                                    IconButton(onClick = closeBottomSheet) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_return),
                                            contentDescription = "Retour"
                                        )
                                    }
                                }
                            },
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
            },
            content = { innerPadding ->
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
                                
                                db.collection("users").document(user.uid)
                                    .set(updates, SetOptions.merge())
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
                        coroutineScope.launch {
                            TravelWowDatabase.getDatabase(context).favoritePostDao().clearAll()
                            onLogout()
                        }
                    },
                    onSave = { newSettings ->
                        coroutineScope.launch {
                            try {
                                db.collection("users").document(user.uid)
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
                                            stepCategory = currentStepCategory,
                                            onStepCategoryChange = { currentStepCategory = it },
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
                                            onSaveDraft = {
                                                isSavingDraft = true
                                                coroutineScope.launch {
                                                    try {
                                                        val draftId = editingDraftId ?: db.collection("users").document(user.uid)
                                                            .collection("drafts").document().id
                                                        
                                                        // Save to local DB first
                                                        val localDraft = LocalDraft(
                                                            id = draftId,
                                                            userId = user.uid,
                                                            title = postTitle,
                                                            description = postDescription,
                                                            stepsJson = Gson().toJson(postSteps),
                                                            createdAt = System.currentTimeMillis(),
                                                            isSynced = false
                                                        )
                                                        draftDao.insertDraft(localDraft)

                                                        // Try to save to Firestore
                                                        try {
                                                            val draftRef = db.collection("users").document(user.uid)
                                                                .collection("drafts").document(draftId)
                                                            
                                                            val draftMap = hashMapOf(
                                                                "title" to postTitle,
                                                                "description" to postDescription,
                                                                "steps" to postSteps.map { step ->
                                                                    hashMapOf(
                                                                        "name" to step.name,
                                                                        "category" to step.category,
                                                                        "latitude" to step.latitude,
                                                                        "longitude" to step.longitude,
                                                                        "images" to step.images
                                                                    )
                                                                },
                                                                "createdAt" to FieldValue.serverTimestamp()
                                                            )
                                                            draftRef.set(draftMap).await()
                                                            
                                                            // Mark as synced if successful
                                                            draftDao.insertDraft(localDraft.copy(isSynced = true))
                                                        } catch (e: Exception) {
                                                            Log.e("TravelWowApp", "Error syncing draft to Firestore, will stay local", e)
                                                        }
                                                        
                                                        // Reset and close
                                                        showCreatePost = false
                                                        postTitle = ""
                                                        postDescription = ""
                                                        postSteps = emptyList()
                                                        editingDraftId = null
                                                        
                                                        // Show success message
                                                        showDraftSuccessDialog = true 
                                                    } catch (e: Exception) {
                                                        Log.e("TravelWowApp", "Error saving draft", e)
                                                    } finally {
                                                        isSavingDraft = false
                                                    }
                                                }
                                            },
                                            isSaving = isSavingDraft,
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
                                            excludeUserId = user.uid,
                                            searchQuery = searchQuery,
                                            filter = postFilter,
                                            onFilterChange = { postFilter = it },
                                            showFilterSheet = showFilterSheet,
                                            onShowFilterSheetChange = { showFilterSheet = it },
                                            focusedPost = focusedPostForMap,
                                            onFocusedPostChange = { post ->
                                                focusedPostForMap = post
                                                if (post == null) {
                                                    closeBottomSheet()
                                                }
                                            },
                                            contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp),
                                            onEmptySpaceClick = closeBottomSheet
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
                                        onFocusedPostChange = { post ->
                                            focusedPostForMap = post
                                            if (post == null) {
                                                closeBottomSheet()
                                            }
                                        },
                                        contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp),
                                        onEmptySpaceClick = closeBottomSheet
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
                                            onLogoutClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        TravelWowDatabase.getDatabase(context).favoritePostDao().clearAll()
                                                        onLogout()
                                                    } catch (e: Exception) {
                                                        onLogout()
                                                    }
                                                }
                                            },
                                            onEditProfileClick = { showEditProfile = true }
                                        )

                                        var selectedProfileTab by remember { mutableIntStateOf(0) }
                                        TabRow(selectedTabIndex = selectedProfileTab) {
                                            Tab(selected = selectedProfileTab == 0, onClick = { selectedProfileTab = 0 }, text = { Text("Publications") })
                                            Tab(selected = selectedProfileTab == 1, onClick = { selectedProfileTab = 1 }, text = { Text("Brouillons") })
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                                        if (selectedProfileTab == 0) {
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
                                                onFocusedPostChange = { post ->
                                                    focusedPostForMap = post
                                                    if (post == null) {
                                                        closeBottomSheet()
                                                    }
                                                },
                                                contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp),
                                                onEmptySpaceClick = closeBottomSheet
                                            )
                                        } else {
                                            DraftsGallery(
                                                userId = user.uid,
                                                draftDao = draftDao,
                                                modifier = Modifier.weight(1f),
                                                onDraftClick = { draft ->
                                                    // Load draft into creation state
                                                    editingDraftId = draft.id
                                                    postTitle = draft.title
                                                    postDescription = draft.description
                                                    postSteps = draft.steps.map { s -> 
                                                        TravelStep(
                                                            name = s.name,
                                                            category = s.category,
                                                            latitude = s.latitude,
                                                            longitude = s.longitude,
                                                            images = s.imageUrls
                                                        )
                                                    }
                                                    showCreatePost = true
                                                    currentDestination = AppDestinations.HOME
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )

        if (showPostSuccessDialog) {
            PostSuccessDialog(onDismiss = { showPostSuccessDialog = false })
        }

        if (showDraftSuccessDialog) {
            DraftSuccessDialog(onDismiss = { showDraftSuccessDialog = false })
        }
    }
}

