package fr.cestnous.travelwow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.cestnous.travelwow.ui.theme.TravelWowTheme

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
    onResetPost: () -> Unit = {}
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
                // "+" / "Cancel" / "Back" Button
                IconButton(
                    onClick = {
                        if (isAddingStep) onBackStepClick()
                        else if (isAdding) onResetPost()
                        else onAddClick()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isAddingStep) R.drawable.ic_return 
                            else if (isAdding) R.drawable.ic_return 
                            else R.drawable.ic_add
                        ),
                        contentDescription = if (isAddingStep || isAdding) "Retour" else "Ajouter",
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
                                IconButton(onClick = { /* TODO */ }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_filters),
                                        contentDescription = "Filtres",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (canShare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    onPostClick: (FirebasePost) -> Unit,
    viewMode: GalleryViewMode,
    modifier: Modifier = Modifier
) {
    PostsGallery(
        onPostClick = onPostClick,
        viewMode = viewMode,
        modifier = modifier
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SearchScreenPreview() {
    TravelWowTheme {
        Column {
            SearchTopBar(searchQuery = "", onSearchQueryChange = {}, onAddClick = {}, viewMode = GalleryViewMode.GRID, onViewModeChange = {})
            SearchScreen(onPostClick = {}, viewMode = GalleryViewMode.GRID)
        }
    }
}
