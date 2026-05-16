package fr.cestnous.travelwow.travelPath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.travelPath.data.model.FirebasePost
import fr.cestnous.travelwow.travelPath.data.model.PostFilter
import fr.cestnous.travelwow.travelPath.ui.theme.TravelWowTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    modifier: Modifier = Modifier,
    title: String = "TravelWow",
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isAdding: Boolean = false,
    isAddingStep: Boolean = false,
    onBackStepClick: () -> Unit = {},
    onConfirmStepClick: () -> Unit = {},
    canConfirmStep: Boolean = false,
    onShareClick: () -> Unit = {},
    canShare: Boolean = false,
    viewMode: GalleryViewMode = GalleryViewMode.GRID,
    onViewModeChange: (GalleryViewMode) -> Unit = {},
    onResetPost: () -> Unit = {},
    isPostSelected: Boolean = false,
    onDeselect: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    isFilterActive: Boolean = false,
    onSwitchClick: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isAdding, isAddingStep, isPostSelected) {
        if (isAdding || isAddingStep || isPostSelected) isSearchActive = false
    }

    CenterAlignedTopAppBar(
        title = {
            if (isAdding || isAddingStep) {
                Text(if (isAddingStep) "Ajouter une étape" else "Nouveau parcours")
            } else if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Recherche...", style = MaterialTheme.typography.bodyLarge) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(title)
            }
        },
        navigationIcon = {
            if (isAdding || isAddingStep || isPostSelected) {
                IconButton(onClick = {
                    if (isAddingStep) onBackStepClick()
                    else if (isAdding) onResetPost()
                    else if (isPostSelected) onDeselect()
                }) {
                    Icon(painterResource(R.drawable.ic_return), contentDescription = "Retour")
                }
            } else if (isSearchActive) {
                IconButton(onClick = { isSearchActive = false; onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            } else if (onSwitchClick != null) {
                IconButton(onClick = onSwitchClick) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Vers TravelShare"
                    )
                }
            }
        },
        actions = {
            if (isAdding || isAddingStep) {
                if (isAddingStep) {
                    TextButton(onClick = onConfirmStepClick, enabled = canConfirmStep) {
                        Text("Ajouter", fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = onShareClick, enabled = canShare) {
                        Text("Partager", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!isPostSelected) {
                if (!isSearchActive) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Rechercher")
                    }
                }
                
                IconButton(onClick = {
                    val newMode = if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                    onViewModeChange(newMode)
                }) {
                    Icon(
                        painter = painterResource(if (viewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel),
                        contentDescription = "Vue"
                    )
                }
                
                IconButton(onClick = onFilterClick) {
                    Box {
                        Icon(painterResource(R.drawable.ic_filters), contentDescription = "Filtres")
                        if (isFilterActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                if (onLogout != null && !isSearchActive) {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Déconnexion")
                    }
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheetContent(
    filter: PostFilter,
    onFilterChange: (PostFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf("Restauration", "Loisirs", "Découvertes", "Culture")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filtres",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Fermer")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Catégories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = filter.selectedCategories.contains(category)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newCategories = if (isSelected) {
                            filter.selectedCategories - category
                        } else {
                            filter.selectedCategories + category
                        }
                        onFilterChange(filter.copy(selectedCategories = newCategories))
                    },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Distance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filter.minDistance.toInt()} - ${filter.maxDistance.toInt()} km",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        RangeSlider(
            value = filter.minDistance..filter.maxDistance,
            onValueChange = { range ->
                onFilterChange(filter.copy(minDistance = range.start, maxDistance = range.endInclusive))
            },
            valueRange = 0f..100f,
            steps = 20,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onFilterChange(PostFilter())
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Réinitialiser")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Appliquer")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPostClick: (FirebasePost) -> Unit,
    viewMode: GalleryViewMode,
    modifier: Modifier = Modifier,
    excludeUserId: String? = null,
    favoritesUserId: String? = null,
    searchQuery: String = "",
    filter: PostFilter = PostFilter(),
    onFilterChange: (PostFilter) -> Unit = {},
    showFilterSheet: Boolean = false,
    onShowFilterSheetChange: (Boolean) -> Unit = {},
    focusedPost: FirebasePost? = null,
    onFocusedPostChange: (FirebasePost?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onEmptySpaceClick: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
    
    Column(modifier = modifier.fillMaxSize()) {
        PostsGallery(
            onPostClick = onPostClick,
            viewMode = viewMode,
            modifier = Modifier.weight(1f),
            excludeUserId = excludeUserId,
            favoritesUserId = favoritesUserId,
            searchQuery = searchQuery,
            filter = filter,
            focusedPost = focusedPost,
            onFocusedPostChange = onFocusedPostChange,
            contentPadding = contentPadding,
            onEmptySpaceClick = onEmptySpaceClick
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onShowFilterSheetChange(false) },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterBottomSheetContent(
                filter = filter,
                onFilterChange = onFilterChange,
                onDismiss = { onShowFilterSheetChange(false) }
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SearchScreenPreview() {
    TravelWowTheme {
        Column {
            SearchTopBar(
                searchQuery = "",
                onSearchQueryChange = {},
                viewMode = GalleryViewMode.GRID,
                onViewModeChange = {})
            SearchScreen(onPostClick = {}, viewMode = GalleryViewMode.GRID)
        }
    }
}
