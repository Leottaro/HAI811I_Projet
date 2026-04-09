package fr.cestnous.travelwow.ui.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.data.repository.PhotoRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: TravelPhoto,
    onBack: () -> Unit,
    onEdit: (TravelPhoto) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PhotoRepository() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // État local pour le like (pour un retour visuel immédiat)
    var likesCount by remember(photo.id) { mutableIntStateOf(photo.likesCount) }
    var isLiked by remember(photo.id, currentUser?.uid) { 
        mutableStateOf(currentUser != null && photo.likedBy.contains(currentUser.uid)) 
    }

    val isAuthor = currentUser != null && currentUser.uid == photo.authorId
    val pagerState = rememberPagerState(pageCount = { photo.imageUrls.size })
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer la publication") },
            text = { Text("Êtes-vous sûr de vouloir supprimer ce voyage ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = repository.deletePhoto(photo.id)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Publication supprimée", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        } else {
                            Toast.makeText(context, "Erreur lors de la suppression", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showDeleteDialog = false
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (isAuthor) {
                        IconButton(onClick = { onEdit(photo) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Galerie d'images
            if (photo.imageUrls.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = photo.imageUrls[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (photo.imageUrls.size > 1) {
                        Row(
                            Modifier.wrapContentHeight().fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(photo.imageUrls.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                Box(modifier = Modifier.padding(4.dp).clip(CircleShape).background(color).size(8.dp))
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(photo.locationName, style = MaterialTheme.typography.headlineMedium)
                        Text("Par @${photo.authorName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    // SECTION LIKE DANS LES DÉTAILS
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$likesCount", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            if (currentUser != null) {
                                scope.launch {
                                    repository.toggleLike(photo.id, currentUser.uid)
                                    // Mise à jour locale immédiate
                                    if (isLiked) {
                                        isLiked = false
                                        likesCount--
                                    } else {
                                        isLiked = true
                                        likesCount++
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Connectez-vous pour liker", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (photo.tags.isNotEmpty()) {
                    Text("Tags: ${photo.tags.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("Description", style = MaterialTheme.typography.titleMedium)
                Text(photo.description)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val uri = if (photo.latitude != null && photo.longitude != null) {
                            Uri.parse("google.navigation:q=${photo.latitude},${photo.longitude}")
                        } else {
                            Uri.parse("google.navigation:q=${photo.locationName}")
                        }
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${photo.locationName}"))
                            context.startActivity(webIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Comment y aller ?")
                }
            }
        }
    }
}
