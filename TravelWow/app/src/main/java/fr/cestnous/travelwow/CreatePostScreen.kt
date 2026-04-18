package fr.cestnous.travelwow

import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Immutable
data class TravelStep(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val images: List<String> = emptyList()
)

@Composable
fun CreatePostContent(
    title: String,
    onTitleChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    steps: List<TravelStep>,
    onAddStep: (TravelStep) -> Unit,
    onRemoveStep: (TravelStep) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddStepDialog by remember { mutableStateOf(false) }

    if (showAddStepDialog) {
        AddStepDialog(
            onDismiss = { showAddStepDialog = false },
            onStepAdded = { step ->
                onAddStep(step)
                showAddStepDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title and Location
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Titre du parcours") },
            placeholder = { Text("Ex: Randonnée au Pic Saint-Loup") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Lieu") },
            placeholder = { Text("Montpellier, France") },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_localisation), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            placeholder = { Text("Racontez votre expérience...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Itinerary / Steps Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Étapes du parcours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${steps.size} étape(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        // List of added steps
        steps.forEach { step ->
            StepItem(step = step, onRemove = { onRemoveStep(step) })
        }

        Button(
            onClick = { showAddStepDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_pin), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ajouter une étape avec photos")
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun StepItem(step: TravelStep, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = step.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Lat: ${"%.4f".format(step.latitude)}, Lon: ${"%.4f".format(step.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Supprimer", modifier = Modifier.size(20.dp))
                }
            }
            
            if (step.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(step.images) { imageUri ->
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun AddStepDialog(
    onDismiss: () -> Unit,
    onStepAdded: (TravelStep) -> Unit
) {
    var stepName by remember { mutableStateOf("") }
    var stepImages by remember { mutableStateOf(emptyList<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    
    // Default position (e.g., Montpellier)
    var selectedLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 12f)
    }

    // Mock existing points (simulating Google Places like restaurants, museums)
    val existingPoints = remember {
        listOf(
            LatLng(43.6107, 3.8767) to "Place de la Comédie (Place)",
            LatLng(43.6115, 3.8735) to "Arc de Triomphe (Monument)",
            LatLng(43.6145, 3.8795) to "Le Corum (Opéra)",
            LatLng(43.6080, 3.8820) to "Hôtel de Ville (Administration)",
            LatLng(43.6090, 3.8750) to "L'Entrecôte (Restaurant)",
            LatLng(43.6120, 3.8780) to "Musée Fabre (Musée)"
        )
    }

    // Mock existing photos from user's "TravelWow gallery"
    val existingPhotos = remember {
        listOf(
            "https://picsum.photos/id/10/200/200",
            "https://picsum.photos/id/11/200/200",
            "https://picsum.photos/id/12/200/200",
            "https://picsum.photos/id/13/200/200"
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            stepImages = stepImages + uris.map { it.toString() }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle étape") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search for Google Maps points
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Rechercher un lieu Google Maps") },
                    placeholder = { Text("Ex: Tour Eiffel, Paris") },
                    trailingIcon = {
                                IconButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        geocoder.getFromLocationName(searchQuery, 1) { addresses ->
                                            if (addresses.isNotEmpty()) {
                                                val address = addresses[0]
                                                val newLatLng = LatLng(address.latitude, address.longitude)
                                                coroutineScope.launch {
                                                    selectedLocation = newLatLng
                                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                                    if (stepName.isBlank()) stepName = address.featureName ?: searchQuery
                                                }
                                            }
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        val addresses = geocoder.getFromLocationName(searchQuery, 1)
                                        if (addresses?.isNotEmpty() == true) {
                                            val address = addresses[0]
                                            val newLatLng = LatLng(address.latitude, address.longitude)
                                            withContext(Dispatchers.Main) {
                                                selectedLocation = newLatLng
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                                if (stepName.isBlank()) stepName = address.featureName ?: searchQuery
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Rechercher")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Suggest existing points (Restaurants, Museums, etc.)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lieux d'intérêt à proximité", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(existingPoints) { (latLng, name) ->
                            val icon = when {
                                name.contains("Restaurant") -> Icons.Default.Restaurant
                                name.contains("Musée") -> Icons.Default.Museum
                                name.contains("Monument") -> Icons.Default.Castle
                                name.contains("Place") -> Icons.Default.Park
                                else -> Icons.Default.Place
                            }
                            SuggestionChip(
                                onClick = {
                                    selectedLocation = latLng
                                    stepName = name.substringBefore(" (")
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                                    }
                                },
                                label = { Text(name.substringBefore(" (")) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = stepName,
                    onValueChange = { stepName = it },
                    label = { Text("Nom de l'étape") },
                    placeholder = { Text("Ex: Belvédère") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Existing Photos Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Utiliser vos photos existantes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(existingPhotos) { photoUrl ->
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!stepImages.contains(photoUrl)) {
                                            stepImages = stepImages + photoUrl
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Text("Photos sélectionnées", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Galerie")
                                Text("Galerie", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(stepImages) { imageUri ->
                        Box(modifier = Modifier.size(80.dp)) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { stepImages = stepImages.filter { it != imageUri } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                Text("Position sur la carte (cliquez sur un lieu ou la carte)", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            selectedLocation = latLng
                        }
                    ) {
                        MapEffect(Unit) { map ->
                            map.setOnPoiClickListener { poi ->
                                selectedLocation = poi.latLng
                                stepName = poi.name
                            }
                        }
                        Marker(
                            state = MarkerState(position = selectedLocation),
                            title = stepName.ifBlank { "Lieu sélectionné" }
                        )
                    }
                }
                
                Text(
                    text = "Lat: ${"%.4f".format(selectedLocation.latitude)}, Lon: ${"%.4f".format(selectedLocation.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                enabled = stepName.isNotBlank(),
                onClick = {
                    onStepAdded(TravelStep(
                        name = stepName.ifBlank { "Étape sans nom" }, 
                        latitude = selectedLocation.latitude, 
                        longitude = selectedLocation.longitude,
                        images = stepImages
                    ))
                }
            ) {
                Text("Ajouter l'étape")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
