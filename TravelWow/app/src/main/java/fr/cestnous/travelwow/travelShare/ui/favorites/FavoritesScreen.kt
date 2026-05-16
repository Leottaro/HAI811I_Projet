package fr.cestnous.travelwow.travelShare.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.ui.feed.PhotoGridItem

@Composable
fun FavoritesScreen(
    onPhotoClick: (TravelPhoto) -> Unit,
    viewModel: FavoritesViewModel = viewModel()
) {
    val favoritePhotos by viewModel.favoritePhotos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Mes Favoris",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (favoritePhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Vous n'avez pas encore de favoris.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoritePhotos, key = { it.id }) { photo ->
                    PhotoGridItem(
                        photo = photo,
                        isLiked = true, // Par définition ils sont likés ici
                        onLikeClick = { /* Dans les favoris, on pourrait proposer de retirer le like */ },
                        onClick = { onPhotoClick(photo) }
                    )
                }
            }
        }
    }
}
