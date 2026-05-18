package fr.cestnous.travelwow.travelPath

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser as AuthUser
import com.google.firebase.firestore.firestore
import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.util.calculateTotalDistance
import fr.cestnous.travelwow.travelPath.util.uploadToCloudinary
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcoursScreen(
    user: AuthUser,
    isFavoriteTab: Boolean = false,
    onLogout: () -> Unit,
    onBackToShare: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val localDb = remember { TravelWowDatabase.getDatabase(context) }
    val draftDao = remember { localDb.draftDao() }

    var userProfile by remember { mutableStateOf<FirebaseUser?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Load user profile
    LaunchedEffect(user.uid) {
        try {
            db.collection("users").document(user.uid).get().await().let { snapshot ->
                if (snapshot.exists()) {
                    userProfile = snapshot.toObject(FirebaseUser::class.java)
                }
            }
        } catch (e: Exception) {
            Log.e("ParcoursScreen", "Error loading profile", e)
        }
    }

    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var focusedPostForMap by remember { mutableStateOf<FirebasePost?>(null) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }

    var postFilter by remember { mutableStateOf(PostFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var showCreatePost by remember { mutableStateOf(false) }
    var showAddStep by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    var isSavingPost by remember { mutableStateOf(false) }
    var showPostSuccessDialog by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    val closeBottomSheet = {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } catch (e: Exception) {
                Log.e("ParcoursScreen", "Error hiding bottom sheet", e)
            }
            selectedPost = null
            focusedPostForMap = null
        }
        Unit
    }

    BackHandler(enabled = sheetState.currentValue != SheetValue.Hidden || showCreatePost || showAddStep) {
        if (showAddStep) showAddStep = false
        else if (showCreatePost) showCreatePost = false
        else if (sheetState.currentValue != SheetValue.Hidden) closeBottomSheet()
    }

    // State for AddStepScreen
    var currentStepName by remember { mutableStateOf("") }
    var currentStepCategory by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (sheetState.currentValue == SheetValue.Hidden) 0.dp else 140.dp,
        sheetDragHandle = { if (sheetState.currentValue != SheetValue.Hidden) BottomSheetDefaults.DragHandle() },
        sheetSwipeEnabled = true,
        sheetContent = {
            DetailsSheetContent(
                post = selectedPost,
                onDismissRequest = closeBottomSheet,
                sheetState = sheetState,
                currentUserProfile = userProfile
            )
        },
        topBar = {
            if (isFavoriteTab) {
                CenterAlignedTopAppBar(
                    title = { Text("Favoris Parcours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackToShare) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = "Retour",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            galleryViewMode = if (galleryViewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                        }) {
                            Icon(
                                imageVector = if (galleryViewMode == GalleryViewMode.GRID) Icons.Default.Map else Icons.Default.ViewModule,
                                contentDescription = "Vue"
                            )
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                onLogout()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Déconnexion",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                SearchTopBar(
                    isAdding = showCreatePost,
                    isAddingStep = showAddStep,
                    onBackStepClick = { showAddStep = false },
                    onConfirmStepClick = {
                        postSteps = postSteps + TravelStep(
                            name = currentStepName.ifBlank { "Étape sans nom" },
                            category = currentStepCategory,
                            latitude = currentStepLocation.latitude,
                            longitude = currentStepLocation.longitude,
                            images = currentStepImages
                        )
                        showAddStep = false
                        currentStepName = ""; currentStepCategory = ""; currentStepImages = emptyList()
                    },
                    canConfirmStep = currentStepName.isNotBlank(),
                    canShare = postTitle.isNotBlank() && postSteps.isNotEmpty() && !isSavingPost,
                    onShareClick = {
                        isSavingPost = true
                        coroutineScope.launch {
                            try {
                                val firebaseSteps = postSteps.mapIndexed { index, step ->
                                    val stepImageUrls = step.images.map { uri ->
                                        if (uri.startsWith("content://") || uri.startsWith("file://")) uploadToCloudinary(uri, context) else uri
                                    }
                                    FirebaseStep(
                                        id = step.id, name = step.name, category = step.category,
                                        latitude = step.latitude, longitude = step.longitude,
                                        imageUrls = stepImageUrls, order = index
                                    )
                                }
                                val postRef = db.collection("travelpath_posts").document()
                                val totalDistance = calculateTotalDistance(postSteps)
                                val post = FirebasePost(
                                    id = postRef.id, authorId = user.uid, title = postTitle,
                                    locationName = firebaseSteps.firstOrNull()?.name ?: "",
                                    description = postDescription,
                                    mainImageUrl = firebaseSteps.flatMap { it.imageUrls }.firstOrNull(),
                                    latitude = firebaseSteps.firstOrNull()?.latitude ?: 0.0,
                                    longitude = firebaseSteps.firstOrNull()?.longitude ?: 0.0,
                                    distanceKm = (totalDistance * 10).roundToInt() / 10.0,
                                    steps = firebaseSteps,
                                    categories = firebaseSteps.map { it.category }.filter { it.isNotBlank() }.distinct()
                                )

                                val batch = db.batch()
                                batch.set(postRef, post)

                                val stepsCollection = postRef.collection("steps")
                                firebaseSteps.forEach { step ->
                                    batch.set(stepsCollection.document(step.id), step)
                                }

                                batch.commit().await()

                                showPostSuccessDialog = true
                                showCreatePost = false
                                postTitle = ""; postDescription = ""; postSteps = emptyList()
                            } catch (e: Exception) {
                                Log.e("ParcoursScreen", "Error sharing post", e)
                            } finally {
                                isSavingPost = false
                            }
                        }
                    },
                    viewMode = galleryViewMode,
                    onViewModeChange = { galleryViewMode = it },
                    isPostSelected = sheetState.currentValue != SheetValue.Hidden,
                    onDeselect = closeBottomSheet,
                    onResetPost = { showCreatePost = false; postTitle = ""; postSteps = emptyList() },
                    onBackToShare = onBackToShare,
                    onLogout = {
                        coroutineScope.launch {
                            onLogout()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            // Content
            Box {
                if (showAddStep) {
                    AddStepScreen(
                        stepName = currentStepName, onStepNameChange = { currentStepName = it },
                        stepCategory = currentStepCategory, onStepCategoryChange = { currentStepCategory = it },
                        stepImages = currentStepImages, onStepImagesChange = { currentStepImages = it },
                        onLocationSelected = { currentStepLocation = it },
                        lastStepLocation = postSteps.lastOrNull()?.let { LatLng(it.latitude, it.longitude) }
                    )
                } else if (showCreatePost) {
                    CreatePostContent(
                        title = postTitle, onTitleChange = { postTitle = it },
                        location = "", onLocationChange = {}, // Added missing parameter
                        description = postDescription, onDescriptionChange = { postDescription = it },
                        steps = postSteps, onAddStepClick = { showAddStep = true },
                        onRemoveStep = { step -> postSteps = postSteps.filter { it.id != step.id } },
                        onStepsChange = { postSteps = it },
                        onSaveDraft = { /* Logic for draft if needed */ },
                        isSaving = false
                    )
                } else if (isFavoriteTab) {
                    PostsGallery(
                        onPostClick = { post ->
                            selectedPost = post
                            focusedPostForMap = post
                            coroutineScope.launch {
                                if (galleryViewMode == GalleryViewMode.GRID) {
                                    sheetState.expand()
                                } else {
                                    sheetState.partialExpand()
                                }
                            }
                        },
                        viewMode = galleryViewMode, favoritesUserId = user.uid,
                        focusedPost = focusedPostForMap, onFocusedPostChange = { focusedPostForMap = it; if (it == null) closeBottomSheet() },
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        onEmptySpaceClick = closeBottomSheet
                    )
                } else {
                    SearchScreen(
                        onPostClick = { post ->
                            selectedPost = post
                            focusedPostForMap = post
                            coroutineScope.launch {
                                if (galleryViewMode == GalleryViewMode.GRID) {
                                    sheetState.expand()
                                } else {
                                    sheetState.partialExpand()
                                }
                            }
                        },
                        viewMode = galleryViewMode,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        filter = postFilter,
                        onFilterChange = { postFilter = it }, showFilterSheet = showFilterSheet,
                        onShowFilterSheetChange = { showFilterSheet = it },
                        focusedPost = focusedPostForMap, onFocusedPostChange = { focusedPostForMap = it; if (it == null) closeBottomSheet() },
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        onEmptySpaceClick = closeBottomSheet
                    )
                }
            }

            // FAB inside Box since BottomSheetScaffold in M3 doesn't have floatingActionButton parameter
            if (!isFavoriteTab && !showCreatePost && !showAddStep) {
                FloatingActionButton(
                    onClick = { showCreatePost = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Publier")
                }
            }
        }
    }

    if (showPostSuccessDialog) {
        AlertDialog(onDismissRequest = { showPostSuccessDialog = false }, confirmButton = { TextButton(onClick = { showPostSuccessDialog = false }) { Text("OK") } }, title = { Text("Succès") }, text = { Text("Votre parcours a été publié !") })
    }
}
