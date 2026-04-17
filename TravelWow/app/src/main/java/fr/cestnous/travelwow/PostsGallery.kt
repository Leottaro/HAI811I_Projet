package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

enum class GalleryViewMode {
    GRID, MAP
}

@Composable
fun PostsGallery(
    selectedItem: Int?,
    onItemClick: (Int) -> Unit,
    viewMode: GalleryViewMode,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (viewMode) {
            GalleryViewMode.GRID -> {
                PostGrid(
                    selectedItem = selectedItem,
                    onItemClick = onItemClick
                )
            }
            GalleryViewMode.MAP -> {
                PostMap(
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
fun PostGrid(
    selectedItem: Int?,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(30) { index ->
            val isSelected = index == selectedItem
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isSelected) {
                            Modifier.border(4.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onItemClick(index) }
            )
        }
    }
}

@Composable
fun PostMap(
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Placeholder for Interactive Map (Google Maps / OSM)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Carte Interactive",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "Les parcours s'afficheront ici sur la carte",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            
            // Mock buttons to simulate clicking a pin on the map
            Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onItemClick(1) }) { Text("Parcours 1") }
                Button(onClick = { onItemClick(2) }) { Text("Parcours 2") }
            }
        }
    }
}
