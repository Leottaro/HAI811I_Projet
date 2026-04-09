package fr.cestnous.travelwow.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.TravelPhoto
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FeedScreen(
    onPhotoClick: (TravelPhoto) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val currentUser = FirebaseAuth.getInstance().currentUser

    Column {
        // BARRE DE RECHERCHE
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.searchPhotos(it) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Rechercher un lieu, un thème...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        // FILTRES PAR TYPE
        FilterRow(onTypeSelected = { viewModel.filterByPlaceType(it) })
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // GRILLE DE PHOTOS (2 colonnes)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoGridItem(
                        photo = photo,
                        isLiked = currentUser != null && photo.likedBy.contains(currentUser.uid),
                        onLikeClick = { viewModel.toggleLike(photo.id) },
                        onClick = { onPhotoClick(photo) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterRow(onTypeSelected: (String) -> Unit) {
    val types = listOf("Tous", "Nature", "Musée", "Rue", "Magasin")
    var selectedType by remember { mutableStateOf("Tous") }

    ScrollableTabRow(
        selectedTabIndex = types.indexOf(selectedType),
        edgePadding = 16.dp,
        divider = {}
    ) {
        types.forEach { type ->
            Tab(
                selected = selectedType == type,
                onClick = { 
                    selectedType = type
                    onTypeSelected(type)
                },
                text = { Text(type) }
            )
        }
    }
}

@Composable
fun PhotoGridItem(
    photo: TravelPhoto, 
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = photo.imageUrls.firstOrNull(),
                contentDescription = photo.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = photo.locationName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${photo.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = photo.placeType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${photo.likesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = onLikeClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                modifier = Modifier.size(16.dp),
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
