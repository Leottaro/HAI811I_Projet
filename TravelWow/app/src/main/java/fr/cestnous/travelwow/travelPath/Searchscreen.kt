package fr.cestnous.travelwow.travelPath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.travelPath.ui.theme.TravelWowTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onAddClick: () -> Unit,
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
    onSwitchClick: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Switch back button (if not in sub-states)
                if (onSwitchClick != null && !isAdding && !isAddingStep && !isPostSelected) {
                    IconButton(onClick = onSwitchClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // "+" / "Cancel" / "Back" / "Return" Button
                IconButton(
                    onClick = {
                        if (isAddingStep) onBackStepClick()
                        else if (isAdding) onResetPost()
                        else if (isPostSelected) onDeselect()
                        else onAddClick()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isAddingStep || isAdding || isPostSelected) R.drawable.ic_return
                            else R.drawable.ic_add
                        ),
                        contentDescription = if (isAddingStep || isAdding || isPostSelected) "Retour" else "Ajouter",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isAdding && !isAddingStep) {
                    // Search TextField
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = "Recherche",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val newMode = if (viewMode == GalleryViewMode.GRID) GalleryViewMode.MAP else GalleryViewMode.GRID
                                    onViewModeChange(newMode)
                                }) {
                                    Icon(
                                        painter = painterResource(if (viewMode == GalleryViewMode.GRID) R.drawable.ic_map else R.drawable.ic_panel),
                                        contentDescription = "Changer de vue",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = onFilterClick) {
                                    Box {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_filters),
                                            contentDescription = "Filtres",
                                            tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )
                } else {
                    // Title
                    Text(
                        text = if (isAddingStep) "Ajouter une étape" else "Nouveau parcours",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isAddingStep) {
                        // Add Step Button
                        TextButton(
                            onClick = onConfirmStepClick,
                            enabled = canConfirmStep
                        ) {
                            Text(
                                "Ajouter",
                                fontWeight = FontWeight.Bold,
                                color = if (canConfirmStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        // Share Button
                        TextButton(
                            onClick = onShareClick,
                            enabled = canShare
                        ) {
                            Text(
                                "Partager",
                                fontWeight = FontWeight.Bold,
                                color = if (canShare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
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
                onAddClick = {},
                viewMode = GalleryViewMode.GRID,
                onViewModeChange = {})
            SearchScreen(onPostClick = {}, viewMode = GalleryViewMode.GRID)
        }
    }
}
