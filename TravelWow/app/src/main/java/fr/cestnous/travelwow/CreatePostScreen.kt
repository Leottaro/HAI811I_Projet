package fr.cestnous.travelwow

import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

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
    onAddStepClick: () -> Unit,
    onRemoveStep: (TravelStep) -> Unit,
    onStepsChange: (List<TravelStep>) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedStepId by remember { mutableStateOf<String?>(null) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    val snapBackOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    // Approximate height for reordering logic
    val itemHeightPx = with(density) { 140.dp.toPx() } 

    val currentStepsList by rememberUpdatedState(steps)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
            }
        }

        itemsIndexed(currentStepsList, key = { _, step -> step.id }) { index, step ->
            val isDragging = draggedStepId == step.id
            val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")
            
            val translationY = if (isDragging) {
                totalDragOffset - (index - initialDragIndex) * itemHeightPx
            } else {
                snapBackOffset.value
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 1f)
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.translationY = translationY
                    }
                    .pointerInput(step.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedStepId = step.id
                                initialDragIndex = index
                                totalDragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragOffset += dragAmount.y

                                val list = currentStepsList
                                val currentIdx = list.indexOfFirst { it.id == draggedStepId }
                                if (currentIdx != -1) {
                                    val targetIdx = (initialDragIndex + (totalDragOffset / itemHeightPx).roundToInt())
                                        .coerceIn(0, list.size - 1)

                                    if (targetIdx != currentIdx) {
                                        val nextIdx = if (targetIdx > currentIdx) currentIdx + 1 else currentIdx - 1
                                        val newList = list.toMutableList()
                                        Collections.swap(newList, currentIdx, nextIdx)
                                        onStepsChange(newList)
                                    }
                                }
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    val currentIdx = currentStepsList.indexOfFirst { it.id == draggedStepId }
                                    if (currentIdx != -1) {
                                        val finalOffset = totalDragOffset - (currentIdx - initialDragIndex) * itemHeightPx
                                        snapBackOffset.snapTo(finalOffset)
                                    }
                                    draggedStepId = null
                                    totalDragOffset = 0f
                                    snapBackOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            },
                            onDragCancel = {
                                draggedStepId = null
                                totalDragOffset = 0f
                            }
                        )
                    }
            ) {
                StepItem(step = step, onRemove = { onRemoveStep(step) })
            }
        }

        item {
            Column {
                Button(
                    onClick = onAddStepClick,
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

@Composable
fun AddStepScreen(
    stepName: String,
    onStepNameChange: (String) -> Unit,
    stepImages: List<String>,
    onStepImagesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onLocationSelected: (LatLng) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }

    // Default position (e.g., Montpellier)
    var selectedLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 12f)
    }

    var currentPoiPhotos by remember { mutableStateOf(emptyList<String>()) }

    // Drag and drop state for images
    var draggedItemUri by remember { mutableStateOf<String?>(null) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    val snapBackOffset = remember { Animatable(0f) }
    
    val density = LocalDensity.current
    val itemWidthPx = remember(density) { with(density) { 88.dp.toPx() } }

    // Use updated state to avoid stale closures in pointerInput
    val currentImagesList by rememberUpdatedState(stepImages)
    val onImagesChangeAction by rememberUpdatedState(onStepImagesChange)

    // Helper to get photos for a POI name
    fun updatePoiPhotos(name: String) {
        val cleanName = name.substringBefore("(").trim()

        // On simule une recherche "Google Images" avec loremflickr (basé sur des tags)
        // C'est beaucoup plus réaliste et ça marche pour n'importe quel lieu
        val searchTerms = if (cleanName.isBlank()) "travel" else cleanName.replace(" ", ",")
        val webPhotos = listOf(
            "https://loremflickr.com/400/300/$searchTerms?lock=1",
            "https://loremflickr.com/400/300/$searchTerms?lock=2",
            "https://loremflickr.com/400/300/$searchTerms?lock=3",
            "https://loremflickr.com/400/300/$searchTerms?lock=4",
            "https://loremflickr.com/400/300/$searchTerms?lock=5"
        )

        currentPoiPhotos = webPhotos.distinct()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            onStepImagesChange(stepImages + uris.map { it.toString() })
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = stepName,
            onValueChange = onStepNameChange,
            label = { Text("Nom de l'étape") },
            placeholder = { Text("Ex: Belvédère") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

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
                                        val name = address.featureName ?: searchQuery
                                        coroutineScope.launch {
                                            selectedLocation = newLatLng
                                            onLocationSelected(newLatLng)
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                            onStepNameChange(name)
                                            updatePoiPhotos(name)
                                        }
                                    }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocationName(searchQuery, 1)
                                if (addresses?.isNotEmpty() == true) {
                                    val address = addresses[0]
                                    val newLatLng = LatLng(address.latitude, address.longitude)
                                    val name = address.featureName ?: searchQuery
                                    withContext(Dispatchers.Main) {
                                        selectedLocation = newLatLng
                                        onLocationSelected(newLatLng)
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                        onStepNameChange(name)
                                        updatePoiPhotos(name)
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

        // Existing Photos Section
        if (currentPoiPhotos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Photos suggérées pour ce lieu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currentPoiPhotos) { photoUrl ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        if (!stepImages.contains(photoUrl)) {
                                            onStepImagesChange(stepImages + photoUrl)
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        Text("Photos sélectionnées", style = MaterialTheme.typography.labelLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(top = 8.dp, end = 8.dp, bottom = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
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
            itemsIndexed(currentImagesList, key = { _, uri -> uri }) { index, imageUri ->
                val isDragging = draggedItemUri == imageUri
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.1f else 1f,
                    label = "dragScale"
                )
                
                // When dragging, we use a raw value to avoid the 1-frame lag of animateFloatAsState
                // which causes the "jump" during swaps.
                val translationX = if (isDragging) {
                    totalDragOffset - (index - initialDragIndex) * itemWidthPx
                } else {
                    snapBackOffset.value
                }

                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(80.dp)
                        .zIndex(if (isDragging) 10f else 1f)
                        .then(if (isDragging) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.translationX = translationX
                        }
                        .pointerInput(imageUri) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItemUri = imageUri
                                    initialDragIndex = index
                                    totalDragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragOffset += dragAmount.x

                                    val list = currentImagesList
                                    val currentIdx = list.indexOf(draggedItemUri)
                                    if (currentIdx != -1) {
                                        val targetIdx = (initialDragIndex + (totalDragOffset / itemWidthPx).roundToInt())
                                            .coerceIn(0, list.size - 1)

                                        if (targetIdx != currentIdx) {
                                            val nextIdx = if (targetIdx > currentIdx) currentIdx + 1 else currentIdx - 1
                                            val newList = list.toMutableList()
                                            Collections.swap(newList, currentIdx, nextIdx)
                                            onImagesChangeAction(newList)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val finalOffset = totalDragOffset - (currentImagesList.indexOf(draggedItemUri) - initialDragIndex) * itemWidthPx
                                        snapBackOffset.snapTo(finalOffset)
                                        draggedItemUri = null
                                        totalDragOffset = 0f
                                        snapBackOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                },
                                onDragCancel = {
                                    draggedItemUri = null
                                    totalDragOffset = 0f
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (!isDragging) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(24.dp)
                                .clickable {
                                    val newList = currentImagesList.toMutableList()
                                    newList.remove(imageUri)
                                    onImagesChangeAction(newList)
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    selectedLocation = latLng
                    onLocationSelected(latLng)
                    currentPoiPhotos = emptyList() // Reset photos when clicking random spot
                }
            ) {
                MapEffect(Unit) { map ->
                    map.setOnPoiClickListener { poi ->
                        selectedLocation = poi.latLng
                        onLocationSelected(poi.latLng)
                        onStepNameChange(poi.name)
                        updatePoiPhotos(poi.name)
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
}

@Composable
fun PostSuccessDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Génial !")
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(
                "Parcours publié !",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Text(
                "Votre aventure est maintenant visible par toute la communauté TravelWow. Merci de partager vos découvertes !",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
