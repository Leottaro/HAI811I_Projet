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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

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
    val montpellier = LatLng(43.6107, 3.8767)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(montpellier, 12f)
    }

    // Mock data for markers
    val mockLocations = listOf(
        LatLng(43.6107, 3.8767) to "Parcours 1",
        LatLng(43.6150, 3.8700) to "Parcours 2",
        LatLng(43.6000, 3.8900) to "Parcours 3"
    )

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        mockLocations.forEachIndexed { index, (location, title) ->
            Marker(
                state = MarkerState(position = location),
                title = title,
                onClick = {
                    onItemClick(index)
                    false
                }
            )
        }
    }
}
