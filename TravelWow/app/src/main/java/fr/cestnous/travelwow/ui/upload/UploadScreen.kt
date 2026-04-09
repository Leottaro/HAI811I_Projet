package fr.cestnous.travelwow.ui.upload

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fr.cestnous.travelwow.data.model.TravelPhoto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onSuccess: () -> Unit,
    editingPhoto: TravelPhoto? = null, // Si non nul, on est en mode édition
    viewModel: UploadViewModel = viewModel()
) {
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var description by remember { mutableStateOf(editingPhoto?.description ?: "") }
    var locationName by remember { mutableStateOf(editingPhoto?.locationName ?: "") }
    var tagsString by remember { mutableStateOf(editingPhoto?.tags?.joinToString(", ") ?: "") }
    var placeType by remember { mutableStateOf(editingPhoto?.placeType ?: "Nature") }
    var expanded by remember { mutableStateOf(false) }
    
    val isUploading by viewModel.isUploading.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> imageUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (editingPhoto == null) "Publier un voyage" else "Modifier mon voyage", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(16.dp))

        // Sélection de plusieurs images
        if (editingPhoto == null) {
            Button(onClick = { launcher.launch("image/*") }) {
                Text("Choisir des photos (${imageUris.size})")
            }
            
            if (imageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.height(120.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imageUris) { uri ->
                        Card {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        } else {
            // En mode édition, on affiche les images existantes (lecture seule pour simplifier)
            LazyRow(modifier = Modifier.height(120.dp)) {
                items(editingPhoto.imageUrls) { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(120.dp), contentScale = ContentScale.Crop)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = locationName,
            onValueChange = { locationName = it },
            label = { Text("Adresse ou Lieu") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Sélecteur de type
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = placeType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type de lieu") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("Nature", "Musée", "Rue", "Magasin", "Autre").forEach { type ->
                    DropdownMenuItem(text = { Text(type) }, onClick = { placeType = type; expanded = false })
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = tagsString,
            onValueChange = { tagsString = it },
            label = { Text("Tags") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(Modifier.height(24.dp))

        if (isUploading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val tagsList = tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    if (editingPhoto == null) {
                        viewModel.uploadPhotos(
                            context, imageUris, description, locationName, placeType, tagsList, onSuccess,
                            { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    } else {
                        viewModel.updatePublication(
                            context, editingPhoto.id, description, locationName, placeType, tagsList, editingPhoto.imageUrls, onSuccess,
                            { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (imageUris.isNotEmpty() || editingPhoto != null) && locationName.isNotBlank()
            ) {
                Text(if (editingPhoto == null) "Publier" else "Enregistrer les modifications")
            }
        }
    }
}
