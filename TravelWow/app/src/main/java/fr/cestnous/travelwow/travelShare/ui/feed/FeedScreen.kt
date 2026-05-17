package fr.cestnous.travelwow.travelShare.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import androidx.lifecycle.viewmodel.compose.viewModel
import android.location.Geocoder // Le Geocoder standard d'Android
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale // Pour Locale.getDefault()
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(datePickerState.selectedDateMillis)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPhotoClick: (TravelPhoto) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    val currentUser = FirebaseAuth.getInstance().currentUser

    Column {
        // BARRE DE RECHERCHE + BOUTON FILTRE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchPhotos(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Rechercher...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filtres")
            }
        }

        // FILTRES PAR TYPE RAPIDE
        FilterRow(onTypeSelected = { viewModel.filterByPlaceType(it) })
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // GRILLE DE PHOTOS
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterBottomSheetContent(
                onUpdate = { type, author, start, end, radius, sort, location ->
                    viewModel.applyFilters(
                        type = if (type == "Tous") null else type,
                        authorName = if (author.isBlank()) null else author,
                        startDate = start,
                        endDate = end,
                        radius = radius?.toDoubleOrNull(),
                        sortOrder = sort,
                        targetLat = location?.first,
                        targetLon = location?.second
                    )
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheetContent(
    onUpdate: (String, String, Long?, Long?, String, SortOrder, Pair<Double, Double>?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf("Tous") }
    var authorName by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var radius by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var locationQuery by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Trigger update automatically on changes without closing
    LaunchedEffect(selectedType, authorName, selectedSort, radius, startDate, endDate, selectedLocation) {
        onUpdate(selectedType, authorName, startDate, endDate, radius, selectedSort, selectedLocation)
    }

    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
        .navigationBarsPadding()) {
        Text("Filtres avancés", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Trier par", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedSort == SortOrder.DATE_DESC,
                onClick = { selectedSort = SortOrder.DATE_DESC },
                label = { Text("Plus récents") }
            )
            FilterChip(
                selected = selectedSort == SortOrder.DATE_ASC,
                onClick = { selectedSort = SortOrder.DATE_ASC },
                label = { Text("Plus anciens") }
            )
            FilterChip(
                selected = selectedSort == SortOrder.AUTHOR_ASC,
                onClick = { selectedSort = SortOrder.AUTHOR_ASC },
                label = { Text("Auteur (A-Z)") }
            )
        }
        Text("Type de lieu", style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Tous", "Nature", "Musée", "Rue", "Magasin", "Autre").forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type) },
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Auteur", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = authorName,
            onValueChange = { authorName = it },
            label = { Text("Pseudo de l'auteur") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("Période", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (startDate == null) "Début" else sdf.format(Date(startDate!!)))
            }

            OutlinedButton(
                onClick = { showEndDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (endDate == null) "Fin" else sdf.format(Date(endDate!!)))
            }
        }

        // Appels des fonctions de dialogue
        if (showStartDatePicker) {
            MyDatePickerDialog(
                onDateSelected = { startDate = it },
                onDismiss = { showStartDatePicker = false }
            )
        }

        if (showEndDatePicker) {
            MyDatePickerDialog(
                onDateSelected = { endDate = it },
                onDismiss = { showEndDatePicker = false }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Lieu de recherche", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = locationQuery,
            onValueChange = { locationQuery = it },
            label = { Text("Ville ou adresse") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocationName(locationQuery, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                withContext(Dispatchers.Main) {
                                    selectedLocation = Pair(address.latitude, address.longitude)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }) { Icon(Icons.Default.Search, null) }
            }
        )

        if (selectedLocation != null) {
            Text("Lieu validé : ${String.format("%.2f", selectedLocation?.first)}, ${String.format("%.2f", selectedLocation?.second)}", color = Color(0xFF4CAF50))
        }

        OutlinedTextField(
            value = radius,
            onValueChange = { radius = it },
            label = { Text("Rayon (km)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Appliquer") }
        Spacer(Modifier.height(32.dp))
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
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        }
    }
}
