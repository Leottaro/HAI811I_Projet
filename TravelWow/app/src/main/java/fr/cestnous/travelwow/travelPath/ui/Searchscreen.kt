package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    modifier: Modifier = Modifier,
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
    onBackToShare: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (isAddingStep) "Ajouter une étape" 
                       else if (isAdding) "Nouveau parcours"
                       else "TravelWow",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isAddingStep) onBackStepClick()
                    else if (isAdding) onResetPost()
                    else if (isPostSelected) onDeselect()
                    else onBackToShare()
                }
            ) {
                Icon(
                    imageVector = if (isAddingStep || isAdding || isPostSelected) Icons.AutoMirrored.Filled.ArrowBack
                                 else Icons.Default.Collections, // Changed from Home to Collections/Gallery
                    contentDescription = "Retour",
                    tint = if (isAddingStep || isAdding || isPostSelected) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            if (isAddingStep) {
                TextButton(onClick = onConfirmStepClick, enabled = canConfirmStep) {
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            } else if (isAdding) {
                TextButton(onClick = onShareClick, enabled = canShare) {
                    Text("Partager", fontWeight = FontWeight.Bold)
                }
            } else if (!isPostSelected) {
                IconButton(onClick = {
                    val newMode = if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                    onViewModeChange(newMode)
                }) {
                    Icon(
                        imageVector = if (viewMode == GalleryViewMode.GRID) Icons.Default.Map else Icons.Default.ViewModule,
                        contentDescription = "Changer de vue"
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Déconnexion", tint = MaterialTheme.colorScheme.error)
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
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}, // Added callback
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
        if (viewMode == GalleryViewMode.GRID && !showFilterSheet) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Rechercher un parcours...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onShowFilterSheetChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtres",
                        tint = if (filter.selectedCategories.isNotEmpty() || filter.minDistance > 0f || filter.maxDistance < 100f) 
                               MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        PostsGallery(
            onPostClick = onPostClick,
            viewMode = viewMode,
            modifier = Modifier.weight(1f),
            excludeUserId = excludeUserId,
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
                viewMode = GalleryViewMode.GRID,
                onViewModeChange = {})
            SearchScreen(onPostClick = {}, viewMode = GalleryViewMode.GRID)
        }
    }
}
