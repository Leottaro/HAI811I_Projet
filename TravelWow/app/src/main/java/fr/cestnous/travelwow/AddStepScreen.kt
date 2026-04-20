package fr.cestnous.travelwow


import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt
import android.location.Geocoder

// Geocoder is free for basic location search.
// Routes API used for travel duration/distance (cheaper than Places API).

suspend fun fetchRouteInfo(origin: LatLng, destination: LatLng): String? = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.MAPS_API_KEY
    val url = URL("https://routes.googleapis.com/directions/v2:computeRoutes")
    try {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-Goog-Api-Key", apiKey)
        connection.setRequestProperty("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
        connection.doOutput = true

        val body = JSONObject().apply {
            put("origin", JSONObject().put("location", JSONObject().put("latLng", JSONObject().apply {
                put("latitude", origin.latitude)
                put("longitude", origin.longitude)
            })))
            put("destination", JSONObject().put("location", JSONObject().put("latLng", JSONObject().apply {
                put("latitude", destination.latitude)
                put("longitude", destination.longitude)
            })))
            put("travelMode", "DRIVE")
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val routes = json.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val distance = route.optInt("distanceMeters") / 1000.0
                val durationStr = route.optString("duration", "0s")
                val durationMin = durationStr.removeSuffix("s").toLongOrNull()?.let { it / 60 } ?: 0
                return@withContext "Distance: %.1f km • Durée: %d min".format(distance, durationMin)
            }
        }
    } catch (e: Exception) {
        Log.e("RoutesAPI", "Error fetching route", e)
    }
    null
}

suspend fun fetchWikidataPhotos(lat: Double, lng: Double, name: String): List<String> = withContext(Dispatchers.IO) {
    val cleanName = name.substringBefore("(").trim().lowercase()
    val radius = if (cleanName.isNotEmpty()) "0.8" else "0.5" // Reduced radius for better relevance
    
    val query = """
        SELECT DISTINCT ?item ?image ?label ?dist WHERE {
          ?item wdt:P18 ?image .
          ?item rdfs:label ?label .
          FILTER(LANG(?label) = "fr" || LANG(?label) = "en")
          SERVICE wikibase:around {
            ?item wdt:P625 ?location .
            bd:serviceParam wikibase:center "Point($lng $lat)"^^geo:wktLiteral .
            bd:serviceParam wikibase:radius "$radius" .
            bd:serviceParam wikibase:distance ?dist .
          }
          ${if (cleanName.isNotEmpty()) "BIND(IF(CONTAINS(LCASE(?label), \"$cleanName\"), 1, 0) AS ?match)" else "BIND(1 AS ?match)"}
        } ORDER BY DESC(?match) ?dist LIMIT 25
    """.trimIndent()

    val url = URL("https://query.wikidata.org/sparql?query=${Uri.encode(query)}&format=json")
    try {
        val connection = url.openConnection() as HttpURLConnection
        // Wikimedia requires an identifiable User-Agent
        val userAgent = "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "application/json")
        
        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val bindings = json.getJSONObject("results").getJSONArray("bindings")
            val photos = mutableListOf<String>()
            for (i in 0 until bindings.length()) {
                val photoUrl = bindings.getJSONObject(i).optJSONObject("image")?.optString("value")
                if (photoUrl != null) {
                    // Extract filename and use thumb.php for reliable scaling and fewer 403s
                    val fileName = photoUrl.substringAfter("Special:FilePath/")
                    val thumbUrl = "https://commons.wikimedia.org/w/thumb.php?f=$fileName&w=800"
                    photos.add(thumbUrl)
                }
            }
            val result = photos.distinct().take(6)
            Log.d("Wikidata", "Found ${result.size} relevant photos for $name")
            return@withContext result
        }
    } catch (e: Exception) {
        Log.e("Wikidata", "Error fetching photos", e)
    }
    emptyList()
}

@Composable
fun AddStepScreen(
    stepName: String,
    onStepNameChange: (String) -> Unit,
    stepImages: List<String>,
    onStepImagesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    lastStepLocation: LatLng? = null,
    onLocationSelected: (LatLng) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        Log.d("AddStepScreen", "AddStepScreen Composable entered")
    }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }

    // Default position (e.g., Montpellier)
    var selectedLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    var routeInfo by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 12f)
    }

    var currentPoiPhotos by remember { mutableStateOf(emptyList<String>()) }

    // Helper to get photos using free Wikidata + loremflickr fallback
    fun updatePoiPhotos(latLng: LatLng, name: String) {
        val displayName = name.ifBlank { searchQuery }
        Log.d("AddStepScreen", "Updating POI photos for: $displayName at $latLng")
        coroutineScope.launch {
            try {
                val wikidataPhotos = fetchWikidataPhotos(latLng.latitude, latLng.longitude, displayName)
                if (wikidataPhotos.isNotEmpty()) {
                    Log.d("AddStepScreen", "Using ${wikidataPhotos.size} Wikidata photos")
                    currentPoiPhotos = wikidataPhotos
                } else {
                    // Fallback to loremflickr
                    Log.d("AddStepScreen", "No Wikidata photos found, falling back to loremflickr")
                    val cleanName = displayName.substringBefore("(").trim()
                    val searchTerms = if (cleanName.isBlank()) "travel" else cleanName.replace(" ", ",")
                    val fallbackPhotos = (1..5).map {
                        "https://loremflickr.com/400/300/$searchTerms?lock=$it"
                    }
                    Log.d("AddStepScreen", "Fallback URLs: $fallbackPhotos")
                    currentPoiPhotos = fallbackPhotos
                }
            } catch (e: Exception) {
                Log.e("AddStepScreen", "Error in updatePoiPhotos", e)
            }
        }
    }

    // Helper to calculate route from previous location
    fun updateLocationInfo(newLatLng: LatLng, name: String) {
        Log.d("AddStepScreen", "updateLocationInfo called for $name at $newLatLng")
        selectedLocation = newLatLng
        onLocationSelected(newLatLng)
        onStepNameChange(name)
        updatePoiPhotos(newLatLng, name)
        
        // Calculate route from previous step if exists
        lastStepLocation?.let { origin ->
            Log.d("AddStepScreen", "Requesting route from $origin to $newLatLng")
            coroutineScope.launch {
                routeInfo = fetchRouteInfo(origin, newLatLng)
            }
        }
    }

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
                    Log.d("AddStepScreen", "Search button clicked with query: $searchQuery")
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                geocoder.getFromLocationName(searchQuery, 1) { addresses ->
                                    if (addresses.isNotEmpty()) {
                                        val address = addresses[0]
                                        Log.d("AddStepScreen", "Address found (Tiramisu+): $address")
                                        val newLatLng = LatLng(address.latitude, address.longitude)
                                        val name = address.featureName ?: searchQuery
                                        coroutineScope.launch {
                                            Log.d("AddStepScreen", "Address found (Tiramisu+): $address")
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                            updateLocationInfo(newLatLng, name)
                                        }
                                    } else {
                                        Log.d("AddStepScreen", "No address found for: $searchQuery")
                                    }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocationName(searchQuery, 1)
                                if (addresses?.isNotEmpty() == true) {
                                    val address = addresses[0]
                                    Log.d("AddStepScreen", "Address found (Legacy): $address")
                                    val newLatLng = LatLng(address.latitude, address.longitude)
                                    val name = address.featureName ?: searchQuery
                                    withContext(Dispatchers.Main) {
                                        Log.d("AddStepScreen", "Address found (Legacy): $address")
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                        updateLocationInfo(newLatLng, name)
                                    }
                                } else {
                                    Log.d("AddStepScreen", "No address found (Legacy) for: $searchQuery")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AddStepScreen", "Geocoder error", e)
                        }
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Rechercher")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Route Info Section
        routeInfo?.let { info ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

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
                        val isSelected = stepImages.contains(photoUrl)
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (isSelected) {
                                        onStepImagesChange(stepImages - photoUrl)
                                    } else {
                                        onStepImagesChange(stepImages + photoUrl)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl)
                                    .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (isSelected) 0.6f else 1f),
                                contentScale = ContentScale.Crop,
                                onError = { state ->
                                    Log.e("AddStepScreen", "AsyncImage error for $photoUrl: ${state.result.throwable}")
                                },
                                onSuccess = {
                                    Log.d("AddStepScreen", "AsyncImage success for $photoUrl")
                                }
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sélectionné",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }
                            }
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
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                            .crossfade(true)
                            .build(),
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
                    updatePoiPhotos(latLng, "")
                    lastStepLocation?.let { origin ->
                        coroutineScope.launch {
                            routeInfo = fetchRouteInfo(origin, latLng)
                        }
                    }
                }
            ) {
                MapEffect(Unit) { map ->
                    map.setOnPoiClickListener { poi ->
                        Log.d("AddStepScreen", "POI clicked: ${poi.name} at ${poi.latLng}")
                        updateLocationInfo(poi.latLng, poi.name)
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
