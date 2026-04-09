package fr.cestnous.travelwow.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.data.model.UserProfile

@Composable
fun ProfileScreen(
    onPhotoClick: (TravelPhoto) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val userPhotos by viewModel.userPhotos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    var username by remember(profile) { mutableStateOf(profile?.username ?: "") }
    var bio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
    var isEditing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadProfilePicture(it) { result ->
                if (result.isFailure) {
                    Toast.makeText(context, result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (isLoading && profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Profil non initialisé.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { isEditing = true }) {
                    Text("Compléter mon profil")
                }
            }
        }
        
        if (isEditing) {
            EditProfileDialog(
                initialUsername = username,
                initialBio = bio,
                onDismiss = { isEditing = false },
                onConfirm = { newUsername, newBio ->
                    if (newUsername.isNotBlank()) {
                        viewModel.createInitialProfile(newUsername, newBio)
                        isEditing = false
                    } else {
                        Toast.makeText(context, "Le pseudo est obligatoire", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // HEADER STYLE INSTAGRAM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo de profil à gauche
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile?.profileImageUrl != null) {
                        AsyncImage(
                            model = profile?.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.width(24.dp))

                // Infos et bouton edit à droite
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${profile?.username}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!profile?.bio.isNullOrBlank()) {
                        Text(
                            text = profile?.bio ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = profile?.email ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // SECTION MES PUBLICATIONS
            Text(
                text = "Mes publications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (userPhotos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Aucune publication pour le moment", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(userPhotos) { photo ->
                        AsyncImage(
                            model = photo.imageUrls.firstOrNull(),
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onPhotoClick(photo) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        if (isEditing) {
            EditProfileDialog(
                initialUsername = username,
                initialBio = bio,
                onDismiss = { isEditing = false },
                onConfirm = { newUsername, newBio ->
                    viewModel.updateProfile(newUsername, newBio) { result ->
                        if (result.isSuccess) {
                            isEditing = false
                        } else {
                            Toast.makeText(context, result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    initialUsername: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var bio by remember { mutableStateOf(initialBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le profil") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Pseudo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(username, bio) }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
