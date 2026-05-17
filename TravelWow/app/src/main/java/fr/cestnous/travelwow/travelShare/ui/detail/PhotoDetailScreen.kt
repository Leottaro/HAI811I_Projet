package fr.cestnous.travelwow.travelShare.ui.detail

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.Comment
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.repository.PhotoRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: TravelPhoto,
    onBack: () -> Unit,
    onEdit: (TravelPhoto) -> Unit,
    onDeleted: () -> Unit,
    onShare: (TravelPhoto) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val repository = remember { PhotoRepository() }
    val userRepository = remember { UserRepository() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    var likesCount by remember(photo.id) { mutableIntStateOf(photo.likesCount) }
    var isLiked by remember(photo.id, currentUser?.uid) { 
        mutableStateOf(currentUser != null && photo.likedBy.contains(currentUser.uid)) 
    }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }

    val isAuthor = currentUser != null && currentUser.uid == photo.authorId
    val pagerState = rememberPagerState(pageCount = { photo.imageUrls.size })
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        repository.getCommentsFlow(photo.id).collectLatest {
            comments = it
        }
    }

    if (showShareDialog) {
        SharePhotoDialog(
            photo = photo,
            onDismiss = { showShareDialog = false }
        )
    }

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
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Signaler cette publication") },
            text = { Text("Souhaitez-vous signaler ce contenu comme inapproprié ?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.reportPhoto(photo.id, currentUser?.uid)

                        Toast.makeText(context, "Merci, le contenu a été signalé", Toast.LENGTH_SHORT).show()
                    }
                    showReportDialog = false
                }) {
                    Text("Signaler", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
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
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Partager")
                    }
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
            if (photo.imageUrls.isNotEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)) {
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
                            Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(photo.imageUrls.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                Box(modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(8.dp))
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Par @${photo.authorName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " • ", // Petit séparateur visuel
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // On suppose que photo possède un champ 'createdAt' (Long)
                            // Sinon, remplace par le nom du champ de date dans ton modèle TravelPhoto
                            val dateText = remember(photo.timestamp) {
                                dateFormatter.format(photo.timestamp.toDate())
                            }
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "$likesCount",
                            style = MaterialTheme.typography.titleMedium,
                            // On ne met pas de padding ici pour que le bouton vienne se coller
                        )

                        // On utilise ce wrapper pour supprimer la marge forcée de 48dp des boutons Material 3
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(
                                onClick = {
                                    if (currentUser != null) {
                                        scope.launch {
                                            val profile = userRepository.getUserProfile(currentUser.uid)
                                            val userName = profile?.username ?: currentUser.email ?: "Anonyme"
                                            repository.toggleLike(photo.id, currentUser.uid, userName)
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
                                },
                                // On retire le .size(40.dp) pour laisser le bouton se compacter au maximum
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Icon(
                                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    modifier = Modifier.size(22.dp), // Taille normale de l'icône
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OutlinedFlag,
                                    contentDescription = "Signaler",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
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

                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Section Commentaires
                Text("Commentaires (${comments.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    comments.forEach { comment ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("@${comment.username}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (currentUser != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ajouter un commentaire...") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (newCommentText.isNotBlank()) {
                                    scope.launch {
                                        val profile = userRepository.getUserProfile(currentUser.uid)
                                        val userName = profile?.username ?: currentUser.email ?: "Anonyme"
                                        val comment = Comment(
                                            userId = currentUser.uid,
                                            username = userName,
                                            text = newCommentText
                                        )
                                        repository.addComment(photo.id, comment)
                                        newCommentText = ""
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
                            }
                        }
                    )
                }
            }
        }
    }
}
