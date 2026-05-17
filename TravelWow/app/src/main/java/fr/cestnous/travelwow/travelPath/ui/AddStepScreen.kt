package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
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
// Routes API used for travel duration/distance.
// Legacy Places API used for POI photos (Nearby Search + Place Details).

// ── Category mapping ─────────────────────────────────────────────────────────

/**
 * Maps a list of legacy Google Places types to one of the four app categories.
 * Returns null if no confident match is found (caller keeps current selection).
 *
 * Reference: https://developers.google.com/maps/documentation/places/web-service/supported_types
 */
fun inferCategory(types: List<String>): String? {
    val restaurationTypes = setOf(
        "restaurant", "cafe", "bakery", "bar", "food", "meal_takeaway",
        "meal_delivery", "night_club", "liquor_store"
    )
    val cultureTypes = setOf(
        "museum", "art_gallery", "library", "church", "hindu_temple",
        "mosque", "synagogue", "place_of_worship"
    )
    val loisirsTypes = setOf(
        "amusement_park", "aquarium", "bowling_alley", "casino",
        "movie_theater", "spa", "stadium", "zoo", "park",
        "campground", "gym", "golf_course"
    )
    val decouvertesTypes = setOf(
        "point_of_interest", "natural_feature", "locality",
        "tourist_attraction", "premise", "establishment"
    )

    // Higher-specificity buckets first; "establishment" / "point_of_interest"
    // are very generic so they sit at the bottom as a last resort.
    for (type in types) {
        when (type) {
            in restaurationTypes -> return "Restauration"
            in cultureTypes      -> return "Culture"
            in loisirsTypes      -> return "Loisirs"
        }
    }
    // Only fall back to Découvertes if no specific type matched
    for (type in types) {
        if (type in decouvertesTypes) return "Découvertes"
    }
    return null
}

// ── Data class ───────────────────────────────────────────────────────────────

data class PlaceInfo(
    val name: String,
    val photoUrls: List<String>,
    val inferredCategory: String?
)

// ── Legacy Places API helper ─────────────────────────────────────────────────

/**
 * Fallback: New Places API (v1) Text Search.
 * Used if Legacy Nearby Search is denied or restricted.
 */
suspend fun fetchPlaceDetailsNewApi(
    query: String,
    lat: Double,
    lng: Double
): PlaceInfo? = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.MAPS_API_KEY
    val url = URL("https://places.googleapis.com/v1/places:searchText")

    try {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Goog-Api-Key", apiKey)

        // Required headers for Android-restricted API keys
        conn.setRequestProperty("X-Android-Package", "fr.cestnous.travelwow")
        // Note: X-Android-Cert is also usually required if the key is restricted by fingerprint.
        // For now we add the package name which is missing according to the error message.

        // Field mask is mandatory in New API
        conn.setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.types,places.photos")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("textQuery", query)
            put("locationBias", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lng)
                    })
                    put("radius", 500.0)
                })
            })
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val places = json.optJSONArray("places")

            if (places != null && places.length() > 0) {
                val place = places.getJSONObject(0)
                val name = place.optJSONObject("displayName")?.optString("text") ?: query
                val typesArr = place.optJSONArray("types")
                val types = if (typesArr != null) {
                    (0 until typesArr.length()).map { typesArr.getString(it) }
                } else emptyList()

                val photosArr = place.optJSONArray("photos")
                val photoUrls = if (photosArr != null) {
                    (0 until minOf(photosArr.length(), 6)).mapNotNull { i ->
                        val photoName = photosArr.getJSONObject(i).optString("name")
                        if (photoName.isNotBlank()) {
                            // New API photo URL format: https://places.googleapis.com/v1/{name}/media?maxHeightPx=400&maxWidthPx=400&key=API_KEY
                            "https://places.googleapis.com/v1/$photoName/media?maxWidthPx=800&key=$apiKey"
                        } else null
                    }
                } else emptyList()

                Log.d("PlacesAPI", "New API (v1) Success: $name")
                return@withContext PlaceInfo(name, photoUrls, inferCategory(types))
            } else {
                Log.w("PlacesAPI", "New API (v1) No results for '$query'")
            }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e("PlacesAPI", "New API (v1) error ${conn.responseCode}: $err")
        }
    } catch (e: Exception) {
        Log.e("PlacesAPI", "New API (v1) exception", e)
    }
    null
}

/**
 * 1. Nearby Search – resolves the best matching place_id and types from
 *    coordinates ± keyword. Does NOT rely on its photo_reference (capped at 1).
 * 2. Place Details – always called to get up to 6 photo_reference tokens,
 *    and to confirm the place name and types.
 *
 * Photo URLs use the Places Photo endpoint:
 *   https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photo_reference=REF&key=KEY
 *
 * Billing:
 *  • Nearby Search          → $0.032 / request  (Basic Data SKU)
 *  • Place Details (Basic)  → $0.017 / request  (always called)
 *  • Place Photo            → $0.007 / photo
 */
suspend fun fetchPlaceDetails(
    lat: Double,
    lng: Double,
    query: String
): PlaceInfo = withContext(Dispatchers.IO) {

    val apiKey = BuildConfig.MAPS_API_KEY
    val fallbackName = query.ifBlank { "Lieu sélectionné" }

    // ── Step 1: Nearby Search — resolve place_id + types from coordinates ─────
    // We only need place_id and types here; photos come from Details (Step 2)
    // because Nearby Search caps photo_reference at 1 per result, while
    // Place Details returns up to 10.
    val keywordParam = if (query.isNotBlank()) "&keyword=${Uri.encode(query)}" else ""
    val nearbyUrl = URL(
        "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=$lat,$lng" +
                "&radius=500" +
                keywordParam +
                "&key=$apiKey"
    )

    var placeName = fallbackName
    var placeTypes: List<String> = emptyList()
    var photoReferences: List<String> = emptyList()
    var placeId: String? = null

    try {
        val conn = nearbyUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        if (conn.responseCode == 200) {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val results = json.optJSONArray("results")

            if (results != null && results.length() > 0) {
                val place = results.getJSONObject(0)
                placeId   = place.optString("place_id").takeIf { it.isNotBlank() }
                placeName = place.optString("name").ifBlank { fallbackName }

                val typesArr = place.optJSONArray("types")
                if (typesArr != null) {
                    placeTypes = (0 until typesArr.length()).map { typesArr.getString(it) }
                }

                Log.d("PlacesAPI", "Nearby: $placeName (id=$placeId) | types=$placeTypes")
            } else {
                val status = json.optString("status")
                val errorMsg = json.optString("error_message")
                Log.w("PlacesAPI", "Nearby Search 0 results for '$query' (status=$status, message=$errorMsg)")

                if (status == "REQUEST_DENIED") {
                    Log.i("PlacesAPI", "Nearby Search denied. Attempting fallback to New API (v1)...")
                    val fallbackInfo = fetchPlaceDetailsNewApi(query, lat, lng)
                    if (fallbackInfo != null) return@withContext fallbackInfo
                }
            }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e("PlacesAPI", "Nearby Search error ${conn.responseCode}: $err")
        }
    } catch (e: Exception) {
        Log.e("PlacesAPI", "Nearby Search exception", e)
    }

    // ── Step 2: Place Details — always fetch the full photo list ────────────
    // Nearby Search returns at most 1 photo_reference; Details returns up to 10.
    // We also re-request name+types here in case Nearby Search found nothing.
    if (placeId != null) {
        try {
            val detailsUrl = URL(
                "https://maps.googleapis.com/maps/api/place/details/json" +
                        "?place_id=$placeId" +
                        "&fields=name,types,photos" +
                        "&key=$apiKey"
            )
            val conn = detailsUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val result = json.optJSONObject("result")

                if (result != null) {
                    // Prefer the Details name (often more complete/correct)
                    result.optString("name").takeIf { it.isNotBlank() }?.let { placeName = it }

                    // Fill types if Nearby gave us none
                    if (placeTypes.isEmpty()) {
                        val typesArr = result.optJSONArray("types")
                        if (typesArr != null) {
                            placeTypes = (0 until typesArr.length()).map { typesArr.getString(it) }
                        }
                    }

                    val photosArr = result.optJSONArray("photos")
                    if (photosArr != null) {
                        photoReferences = (0 until minOf(photosArr.length(), 6))
                            .mapNotNull {
                                photosArr.getJSONObject(it)
                                    .optString("photo_reference")
                                    .takeIf { r -> r.isNotBlank() }
                            }
                    }

                    Log.d("PlacesAPI", "Details: $placeName | photos=${photoReferences.size}")
                } else {
                    val status = json.optString("status")
                    val errorMsg = json.optString("error_message")
                    Log.w("PlacesAPI", "Place Details 0 results (status=$status, message=$errorMsg)")
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("PlacesAPI", "Place Details error ${conn.responseCode}: $err")
            }
        } catch (e: Exception) {
            Log.e("PlacesAPI", "Place Details exception", e)
        }
    }

    // ── Build photo URLs ─────────────────────────────────────────────────────
    val photoUrls = photoReferences.map { ref ->
        "https://maps.googleapis.com/maps/api/place/photo" +
                "?maxwidth=800&photo_reference=$ref&key=$apiKey"
    }

    PlaceInfo(
        name = placeName,
        photoUrls = photoUrls,
        inferredCategory = inferCategory(placeTypes)
    )
}

// ── Routes API helper (unchanged) ────────────────────────────────────────────

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
                return@withContext "Distance: %.1f km".format(distance)
            } else {
                Log.w("RoutesAPI", "No routes found: $response")
            }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e("RoutesAPI", "Routes API error ${connection.responseCode}: $err")
        }
    } catch (e: Exception) {
        Log.e("RoutesAPI", "Error fetching route", e)
    }
    null
}

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun AddStepScreen(
    stepName: String,
    onStepNameChange: (String) -> Unit,
    stepCategory: String,
    onStepCategoryChange: (String) -> Unit,
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

    val scrollState = rememberScrollState()
    var mapIsInteracting by remember { mutableStateOf(false) }

    var selectedLocation by remember { mutableStateOf(LatLng(43.6107, 3.8767)) }
    var routeInfo by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 12f)
    }

    var currentPoiPhotos by remember { mutableStateOf(emptyList<String>()) }

    val categories = listOf(
        "Restauration" to Icons.Default.Restaurant,
        "Loisirs"      to Icons.Default.Hiking,
        "Découvertes"  to Icons.Default.Explore,
        "Culture"      to Icons.Default.AccountBalance
    )

    // ── Core helper ───────────────────────────────────────────────────────────
    fun updatePlaceInfo(latLng: LatLng, poiName: String) {
        val queryName = poiName.ifBlank { searchQuery }
        Log.d("AddStepScreen", "updatePlaceInfo: '$queryName' at $latLng")

        coroutineScope.launch {
            val info = fetchPlaceDetails(latLng.latitude, latLng.longitude, queryName)
            Log.d("AddStepScreen", "infos: '$info'")

            selectedLocation = latLng
            onLocationSelected(latLng)
            onStepNameChange(info.name)

            info.inferredCategory?.let { cat ->
                Log.d("AddStepScreen", "Auto-selecting category: $cat")
                onStepCategoryChange(cat)
            }

            currentPoiPhotos = info.photoUrls.ifEmpty {
                // Fallback: loremflickr keyed on place name
                val terms = info.name.substringBefore("(").trim()
                    .replace(" ", ",").ifBlank { "travel" }
                (1..5).map { "https://loremflickr.com/400/300/$terms?lock=$it" }
            }

            lastStepLocation?.let { origin ->
                routeInfo = fetchRouteInfo(origin, latLng)
            }
        }
    }

    // ── Drag & drop state ─────────────────────────────────────────────────────
    var draggedItemUri by remember { mutableStateOf<String?>(null) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    val snapBackOffset = remember { Animatable(0f) }

    val density = LocalDensity.current
    val itemWidthPx = remember(density) { with(density) { 88.dp.toPx() } }

    val currentImagesList by rememberUpdatedState(stepImages)
    val onImagesChangeAction by rememberUpdatedState(onStepImagesChange)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            onStepImagesChange(stepImages + uris.map { it.toString() })
        }
    )

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = !mapIsInteracting)
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Catégorie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { (name, icon) ->
                    FilterChip(
                        selected = stepCategory == name,
                        onClick = { onStepCategoryChange(name) },
                        label = { Text(name) },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Rechercher un lieu") },
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
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                            updatePlaceInfo(newLatLng, searchQuery)
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
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                        updatePlaceInfo(newLatLng, searchQuery)
                                    }
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
                                    if (isSelected) onStepImagesChange(stepImages - photoUrl)
                                    else onStepImagesChange(stepImages + photoUrl)
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (isSelected) 0.6f else 1f),
                                contentScale = ContentScale.Crop,
                                onError = { state ->
                                    Log.e("AddStepScreen", "AsyncImage error for $photoUrl: ${state.result.throwable}")
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
                .height(400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        mapIsInteracting = true
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        mapIsInteracting = false
                    }
                }
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    updatePlaceInfo(latLng, "")
                }
            ) {
                MapEffect(Unit) { map ->
                    map.setOnPoiClickListener { poi ->
                        Log.d("AddStepScreen", "POI clicked: ${poi.name} at ${poi.latLng}")
                        updatePlaceInfo(poi.latLng, poi.name)
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(poi.latLng, 15f))
                        }
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