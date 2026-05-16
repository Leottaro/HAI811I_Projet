package fr.cestnous.travelwow.travelPath

import android.content.Context
import android.util.Log
import kotlin.math.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.travelPath.data.local.LocalDraft
import fr.cestnous.travelwow.travelPath.data.local.TravelWowDatabase
import fr.cestnous.travelwow.travelPath.data.model.FirebaseNotification
import fr.cestnous.travelwow.travelPath.data.model.FirebasePost
import fr.cestnous.travelwow.travelPath.data.model.FirebaseStep
import fr.cestnous.travelwow.travelPath.data.model.FirebaseUser
import fr.cestnous.travelwow.travelPath.data.model.FirebaseUserSettings
import fr.cestnous.travelwow.travelPath.data.model.NotificationType
import fr.cestnous.travelwow.travelPath.data.model.PostFilter
import fr.cestnous.travelwow.travelPath.ui.profile.EditProfileScreen
import fr.cestnous.travelwow.travelPath.ui.screen.AddStepScreen
import fr.cestnous.travelwow.travelPath.ui.screen.CreatePostContent
import fr.cestnous.travelwow.travelPath.ui.screen.DetailsSheetContent
import fr.cestnous.travelwow.travelPath.ui.screen.DraftSuccessDialog
import fr.cestnous.travelwow.travelPath.ui.screen.DraftsGallery
import fr.cestnous.travelwow.travelPath.ui.screen.PostSuccessDialog
import fr.cestnous.travelwow.travelPath.ui.screen.TravelStep
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.collections.get

suspend fun uploadToCloudinary(uri: String, context: Context): String = suspendCancellableCoroutine { continuation ->
    MediaManager.get().upload(uri.toUri())
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

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Earth's radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun calculateTotalDistance(steps: List<TravelStep>): Double {
    if (steps.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until steps.size - 1) {
        val s1 = steps[i]
        val s2 = steps[i + 1]
        total += calculateDistance(s1.latitude, s1.longitude, s2.latitude, s2.longitude)
    }
    return total
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
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val localDb = remember { TravelWowDatabase.getDatabase(context) }
    val draftDao = remember { localDb.draftDao() }

    LaunchedEffect(user.uid) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) return@OnCompleteListener
                TravelWowMessagingService.updateTokenInFirestore(user.uid, task.result)
            })

            db.collection("users").document(user.uid).addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    userProfile = snapshot.toObject(FirebaseUser::class.java)
                }
            }

            val countSnapshot = db.collection("travelpath_posts").whereEqualTo("authorId", user.uid).get().await()
            userPostCount = countSnapshot.size()

            db.collection("notifications").whereEqualTo("recipientId", user.uid).whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    snapshot?.documentChanges?.forEach { dc ->
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val notif = dc.document.toObject(FirebaseNotification::class.java)
                            TravelWowMessagingService.sendLocalNotification(context, notif.title, notif.message)
                            db.collection("notifications").document(dc.document.id).update("isRead", true)
                        }
                    }
                }
        } catch (e: Exception) { e.printStackTrace() }
    }

    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var focusedPostForMap by remember { mutableStateOf<FirebasePost?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    
    var postFilter by remember { mutableStateOf(PostFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val isFilterActive = postFilter.selectedCategories.isNotEmpty() || postFilter.minDistance > 0f || postFilter.maxDistance < 100f

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
            text = { Text("Voulez-vous vraiment vous déconnecter ?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    coroutineScope.launch {
                        TravelWowDatabase.getDatabase(context).favoritePostDao().clearAll()
                        onLogout()
                    }
                }) { Text("Se déconnecter", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Annuler") } }
        )
    }

    var currentStepName by remember { mutableStateOf("") }
    var currentStepCategory by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    
    val closeBottomSheet = {
        showBottomSheet = false
        coroutineScope.launch {
            try { sheetState.partialExpand() } catch (e: Exception) {}
            selectedPost = null
            focusedPostForMap = null
        }
        Unit
    }

    LaunchedEffect(currentDestination) { closeBottomSheet() }

    BackHandler(enabled = showBottomSheet || showCreatePost || showAddStep || showSettings || showEditProfile) {
        if (showAddStep) showAddStep = false
        else if (showCreatePost) showCreatePost = false
        else if (showEditProfile) showEditProfile = false
        else if (showSettings) showSettings = false
        else if (showBottomSheet) closeBottomSheet()
    }

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(painterResource(it.icon), contentDescription = it.label) },
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
        Box(modifier = Modifier.fillMaxSize()) {
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = if (showBottomSheet) 140.dp else 0.dp,
                sheetDragHandle = { if (showBottomSheet) BottomSheetDefaults.DragHandle() },
                sheetSwipeEnabled = showBottomSheet,
                sheetContent = {
                    DetailsSheetContent(post = selectedPost, onDismissRequest = closeBottomSheet, sheetState = sheetState, currentUserProfile = userProfile)
                },
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (!showSettings && !showEditProfile) {
                        when (currentDestination) {
                            AppDestinations.HOME, AppDestinations.FAVORITES -> SearchTopBar(
                                title = if (currentDestination == AppDestinations.FAVORITES) "Mes Favoris" else "TravelWow",
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onResetPost = {
                                    showCreatePost = false
                                    postTitle = ""; postLocation = ""; postDescription = ""; postSteps = emptyList(); editingDraftId = null
                                },
                                isAdding = showCreatePost,
                                isAddingStep = showAddStep,
                                onBackStepClick = { showAddStep = false; currentStepName = ""; currentStepCategory = ""; currentStepImages = emptyList() },
                                onConfirmStepClick = {
                                    postSteps = postSteps + TravelStep(name = currentStepName.ifBlank { "Étape sans nom" }, category = currentStepCategory, latitude = currentStepLocation.latitude, longitude = currentStepLocation.longitude, images = currentStepImages)
                                    showAddStep = false; currentStepName = ""; currentStepCategory = ""; currentStepImages = emptyList()
                                },
                                canConfirmStep = currentStepName.isNotBlank(),
                                canShare = postTitle.isNotBlank() && postSteps.isNotEmpty() && !isSavingPost && !showAddStep,
                                onShareClick = {
                                    isSavingPost = true
                                    coroutineScope.launch {
                                        try {
                                            val firebaseSteps = postSteps.mapIndexed { index, step ->
                                                val stepImageUrls = step.images.map { uri ->
                                                    if (uri.startsWith("content://") || uri.startsWith("file://")) uploadToCloudinary(uri, context) else uri
                                                }
                                                FirebaseStep(id = step.id, name = step.name, category = step.category, latitude = step.latitude, longitude = step.longitude, imageUrls = stepImageUrls, order = index)
                                            }
                                            val postRef = db.collection("travelpath_posts").document()
                                            val postId = postRef.id
                                            val totalDistance = calculateTotalDistance(postSteps)
                                            val post = FirebasePost(id = postId, authorId = user.uid, title = postTitle, locationName = firebaseSteps.firstOrNull()?.name ?: "", description = postDescription, mainImageUrl = firebaseSteps.flatMap { it.imageUrls }.firstOrNull(), latitude = firebaseSteps.firstOrNull()?.latitude ?: 0.0, longitude = firebaseSteps.firstOrNull()?.longitude ?: 0.0, distanceKm = (totalDistance * 10).roundToInt() / 10.0, steps = firebaseSteps, categories = firebaseSteps.map { it.category }.filter { it.isNotBlank() }.distinct())
                                            postRef.set(post).await()
                                            val stepsCollection = postRef.collection("steps")
                                            firebaseSteps.forEach { stepsCollection.document(it.id).set(it).await() }
                                            showPostSuccessDialog = true
                                            showCreatePost = false; postTitle = ""; postLocation = ""; postDescription = ""; postSteps = emptyList(); editingDraftId = null
                                        } catch (e: Exception) { e.printStackTrace() } finally { isSavingPost = false }
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
                            AppDestinations.PROFILE -> {}
                        }
                    }
                }
            ) { innerPadding ->
                if (showEditProfile) {
                    EditProfileScreen(userId = user.uid, currentUsername = userProfile?.username ?: "Utilisateur", currentBio = userProfile?.bio ?: "", currentPhotoUri = userProfile?.photoUrl, onBack = { showEditProfile = false }, onSave = { newName, newBio, newPhotoUri ->
                        coroutineScope.launch {
                            try {
                                var finalPhotoUrl = newPhotoUri
                                if (newPhotoUri != null && (newPhotoUri.startsWith("content://") || newPhotoUri.startsWith("file://"))) finalPhotoUrl = uploadToCloudinary(newPhotoUri, context)
                                val updates = mutableMapOf<String, Any>("username" to newName, "bio" to newBio)
                                finalPhotoUrl?.let { updates["photoUrl"] = it }
                                db.collection("users").document(user.uid).set(updates, SetOptions.merge()).await()
                                userProfile = userProfile?.copy(username = newName, bio = newBio, photoUrl = finalPhotoUrl)
                                showEditProfile = false
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }, modifier = Modifier.fillMaxSize())
                } else if (showSettings) {
                    SettingsScreen(settings = userProfile?.settings ?: FirebaseUserSettings(), userEmail = user.email ?: "Utilisateur", onBack = { showSettings = false }, onLogout = onLogout, onSave = { newSettings ->
                        coroutineScope.launch {
                            try {
                                db.collection("users").document(user.uid).update("settings", newSettings).await()
                                userProfile = userProfile?.copy(settings = newSettings)
                                showSettings = false
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentDestination) {
                            AppDestinations.HOME -> {
                                if (showAddStep) {
                                    AddStepScreen(stepName = currentStepName, onStepNameChange = { currentStepName = it }, stepCategory = currentStepCategory, onStepCategoryChange = { currentStepCategory = it }, stepImages = currentStepImages, onStepImagesChange = { currentStepImages = it }, onLocationSelected = { currentStepLocation = it }, lastStepLocation = postSteps.lastOrNull()?.let { LatLng(it.latitude, it.longitude) })
                                } else if (showCreatePost) {
                                    CreatePostContent(title = postTitle, onTitleChange = { postTitle = it }, location = postLocation, onLocationChange = { postLocation = it }, description = postDescription, onDescriptionChange = { postDescription = it }, steps = postSteps, onAddStepClick = { showAddStep = true }, onRemoveStep = { step -> postSteps = postSteps.filter { it.id != step.id } }, onStepsChange = { postSteps = it }, onSaveDraft = {
                                        isSavingDraft = true
                                        coroutineScope.launch {
                                            try {
                                                val draftId = editingDraftId ?: db.collection("users").document(user.uid).collection("drafts").document().id
                                                val localDraft = LocalDraft(id = draftId, userId = user.uid, title = postTitle, description = postDescription, stepsJson = Gson().toJson(postSteps), createdAt = System.currentTimeMillis(), isSynced = false)
                                                draftDao.insertDraft(localDraft)
                                                showCreatePost = false; postTitle = ""; postDescription = ""; postSteps = emptyList(); editingDraftId = null; showDraftSuccessDialog = true
                                            } catch (e: Exception) { e.printStackTrace() } finally { isSavingDraft = false }
                                        }
                                    }, isSaving = isSavingDraft)
                                } else {
                                    SearchScreen(onPostClick = { selectedPost = it; focusedPostForMap = it; showBottomSheet = true }, viewMode = galleryViewMode, excludeUserId = user.uid, searchQuery = searchQuery, filter = postFilter, onFilterChange = { postFilter = it }, showFilterSheet = showFilterSheet, onShowFilterSheetChange = { showFilterSheet = it }, focusedPost = focusedPostForMap, onFocusedPostChange = { focusedPostForMap = it; if (it == null) closeBottomSheet() }, contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp), onEmptySpaceClick = closeBottomSheet)
                                }
                            }
                            AppDestinations.FAVORITES -> PostsGallery(onPostClick = { selectedPost = it; focusedPostForMap = it; showBottomSheet = true }, viewMode = galleryViewMode, favoritesUserId = user.uid, focusedPost = focusedPostForMap, onFocusedPostChange = { focusedPostForMap = it; if (it == null) closeBottomSheet() }, contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp), onEmptySpaceClick = closeBottomSheet)
                            AppDestinations.PROFILE -> Column(modifier = Modifier.fillMaxSize()) {
                                ProfileHeader(username = userProfile?.username ?: "Utilisateur", bio = userProfile?.bio ?: "", photoUri = userProfile?.photoUrl, postsCount = userPostCount, followersCount = userProfile?.followersCount ?: 0, followingCount = userProfile?.followingCount ?: 0, viewMode = galleryViewMode, onViewModeChange = { galleryViewMode = it }, onSettingsClick = { showSettings = true }, onLogoutClick = { showLogoutDialog = true }, onEditProfileClick = { showEditProfile = true })
                                var selectedProfileTab by remember { mutableIntStateOf(0) }
                                TabRow(selectedTabIndex = selectedProfileTab) {
                                    Tab(selected = selectedProfileTab == 0, onClick = { selectedProfileTab = 0 }, text = { Text("Publications") })
                                    Tab(selected = selectedProfileTab == 1, onClick = { selectedProfileTab = 1 }, text = { Text("Brouillons") })
                                }
                                if (selectedProfileTab == 0) {
                                    PostsGallery(onPostClick = { selectedPost = it; focusedPostForMap = it; showBottomSheet = true }, viewMode = galleryViewMode, userIdFilter = user.uid, focusedPost = focusedPostForMap, onFocusedPostChange = { focusedPostForMap = it; if (it == null) closeBottomSheet() }, contentPadding = PaddingValues(bottom = if (showBottomSheet) 140.dp else 0.dp), onEmptySpaceClick = closeBottomSheet)
                                } else {
                                    DraftsGallery(userId = user.uid, draftDao = draftDao, onDraftClick = { draft ->
                                        editingDraftId = draft.id; postTitle = draft.title; postDescription = draft.description; postSteps = draft.steps.map { s -> TravelStep(name = s.name, category = s.category, latitude = s.latitude, longitude = s.longitude, images = s.imageUrls) }
                                        showCreatePost = true; currentDestination = AppDestinations.HOME
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Bouton Flottant (FAB) positionné manuellement
            if (!showCreatePost && !showAddStep && !showBottomSheet && currentDestination == AppDestinations.HOME && !showSettings && !showEditProfile) {
                FloatingActionButton(
                    onClick = { showCreatePost = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 80.dp) // Pour ne pas chevaucher la nav bar
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Créer un parcours")
                }
            }
        }

        if (showPostSuccessDialog) PostSuccessDialog(onDismiss = { showPostSuccessDialog = false })
        if (showDraftSuccessDialog) DraftSuccessDialog(onDismiss = { showDraftSuccessDialog = false })
    }
}
enum class AppDestinations(val label: String, val icon: Int) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (photoUri != null) AsyncImage(model = photoUri, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(painter = painterResource(R.drawable.ic_account_box), contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(32.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat(label = "Publications", value = postsCount.toString())
                ProfileStat(label = "Abonnés", value = followersCount.toString())
                ProfileStat(label = "Abonnements", value = followingCount.toString())
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(onClick = { onViewModeChange(if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painter = painterResource(if (viewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel), contentDescription = "Vue", modifier = Modifier.size(20.dp)) }
                }
                Surface(onClick = onSettingsClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = "Paramètres", modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Text(text = bio, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Button(onClick = onEditProfileClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
            Text("Modifier le profil", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
