package fr.cestnous.travelwow.travelPath

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser as AuthUser
import com.google.firebase.firestore.firestore
import fr.cestnous.travelwow.travelPath.data.model.PostFilter
import fr.cestnous.travelwow.travelPath.GalleryViewMode
import fr.cestnous.travelwow.travelPath.data.local.TravelWowDatabase
import fr.cestnous.travelwow.travelPath.data.model.FirebasePost
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.screen.AddStepScreen
import fr.cestnous.travelwow.travelPath.ui.screen.CreatePostContent
import fr.cestnous.travelwow.travelPath.ui.screen.TravelStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcoursScreen(
    user: AuthUser,
    title: String,
    onLogout: () -> Unit,
    onBackToShare: () -> Unit,
    modifier: Modifier = Modifier,
    showFavoritesOnly: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val localDb = remember { TravelWowDatabase.getDatabase(context) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreatePost by remember { mutableStateOf(false) }
    var showAddStep by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postLocation by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    var isSavingPost by remember { mutableStateOf(false) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    var postFilter by remember { mutableStateOf(PostFilter()) }

    // État pour AddStepScreen
    var currentStepName by remember { mutableStateOf("") }
    var currentStepCategory by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }

    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)

    BackHandler(enabled = showCreatePost || showAddStep || showBottomSheet) {
        if (showAddStep) showAddStep = false
        else if (showCreatePost) showCreatePost = false
        else if (showBottomSheet) showBottomSheet = false
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onResetPost = { showCreatePost = false },
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
                onShareClick = { /* Logique de partage */ },
                onFilterClick = { /* ... */ },
                isFilterActive = false,
                viewMode = galleryViewMode,
                onViewModeChange = { galleryViewMode = it },
                isPostSelected = showBottomSheet,
                onDeselect = { showBottomSheet = false },
                onSwitchClick = onBackToShare,
                onLogout = onLogout
            )
        },
        floatingActionButton = {
            if (!showCreatePost && !showAddStep && !showBottomSheet && !showFavoritesOnly) {
                FloatingActionButton(onClick = { showCreatePost = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Créer un parcours")
                }
            }
        }
    ) { padding ->
        Box(modifier = modifier.padding(padding)) {
            if (showAddStep) {
                AddStepScreen(
                    stepName = currentStepName,
                    onStepNameChange = { currentStepName = it },
                    stepCategory = currentStepCategory,
                    onStepCategoryChange = { currentStepCategory = it },
                    stepImages = currentStepImages,
                    onStepImagesChange = { currentStepImages = it },
                    onLocationSelected = { currentStepLocation = it },
                    lastStepLocation = postSteps.lastOrNull()?.let { LatLng(it.latitude, it.longitude) }
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
                    onSaveDraft = { /* ... */ },
                    isSaving = false
                )
            } else {
                SearchScreen(
                    onPostClick = { selectedPost = it; showBottomSheet = true },
                    viewMode = galleryViewMode,
                    searchQuery = searchQuery,
                    excludeUserId = if (showFavoritesOnly) null else user.uid,
                    favoritesUserId = if (showFavoritesOnly) user.uid else null,
                    filter = postFilter,
                    onFilterChange = { postFilter = it }
                )
            }
        }
    }
}
