package fr.cestnous.travelwow.travelPath

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser as AuthUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import fr.cestnous.travelwow.travelPath.FirebasePost
import fr.cestnous.travelwow.travelPath.FirebaseStep
import fr.cestnous.travelwow.travelPath.FirebaseUser
import fr.cestnous.travelwow.travelPath.TravelStep
import fr.cestnous.travelwow.travelPath.TravelWowDatabase
import fr.cestnous.travelwow.travelPath.LocalDraft
import fr.cestnous.travelwow.travelPath.GalleryViewMode
import fr.cestnous.travelwow.travelPath.PostFilter
import fr.cestnous.travelwow.travelPath.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcoursScreen(
    user: AuthUser,
    onLogout: () -> Unit,
    onBackToShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val localDb = remember { TravelWowDatabase.getDatabase(context) }
    val draftDao = remember { localDb.draftDao() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreatePost by remember { mutableStateOf(false) }
    var showAddStep by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postLocation by remember { mutableStateOf("") }
    var postDescription by remember { mutableStateOf("") }
    var postSteps by remember { mutableStateOf(emptyList<TravelStep>()) }
    var isSavingPost by remember { mutableStateOf(false) }
    var editingDraftId by remember { mutableStateOf<String?>(null) }
    var galleryViewMode by rememberSaveable { mutableStateOf(GalleryViewMode.GRID) }
    var postFilter by remember { mutableStateOf(PostFilter()) }

    // État pour AddStepScreen
    var currentStepName by remember { mutableStateOf("") }
    var currentStepCategory by remember { mutableStateOf("") }
    var currentStepImages by remember { mutableStateOf(emptyList<String>()) }
    var currentStepLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = showCreatePost || showAddStep || showBottomSheet) {
        if (showAddStep) showAddStep = false
        else if (showCreatePost) showCreatePost = false
        else if (showBottomSheet) showBottomSheet = false
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onAddClick = { showCreatePost = true },
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
                onShareClick = { /* Logique de partage Firebase */ },
                onFilterClick = { /* ... */ },
                isFilterActive = false,
                viewMode = galleryViewMode,
                onViewModeChange = { galleryViewMode = it },
                isPostSelected = showBottomSheet,
                onDeselect = { showBottomSheet = false },
                onSwitchClick = onBackToShare
            )
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
                    onSaveDraft = { /* Logique Brouillon */ },
                    isSaving = false
                )
            } else {
                SearchScreen(
                    onPostClick = { selectedPost = it; showBottomSheet = true },
                    viewMode = galleryViewMode,
                    searchQuery = searchQuery,
                    excludeUserId = user.uid,
                    filter = postFilter,
                    onFilterChange = { postFilter = it }
                )
            }
        }
    }
}
