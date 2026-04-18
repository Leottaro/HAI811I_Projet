package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class GalleryViewMode {
    GRID, MAP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsGallery(
    selectedItem: Int?,
    onItemClick: (Int) -> Unit,
    viewMode: GalleryViewMode,
    modifier: Modifier = Modifier
) {
    val db = remember { Firebase.firestore }
    var posts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val fetchPosts = {
        coroutineScope.launch {
            isRefreshing = true
            try {
                // Fetch posts ordered by creation date
                val snapshot = db.collection("posts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()
                
                posts = snapshot.toObjects(FirebasePost::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchPosts()
    }

    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { fetchPosts() },
        state = state,
        modifier = modifier.fillMaxSize()
    ) {
        when (viewMode) {
            GalleryViewMode.GRID -> {
                PostGrid(
                    posts = posts,
                    selectedItem = selectedItem,
                    onItemClick = onItemClick
                )
            }
            GalleryViewMode.MAP -> {
                PostMap(
                    posts = posts,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
fun PostGrid(
    posts: List<FirebasePost>,
    selectedItem: Int?,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                val isSelected = index == selectedItem
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (isSelected) {
                                Modifier.border(4.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onItemClick(index) }
                ) {
                    if (post.mainImageUrl != null) {
                        AsyncImage(
                            model = post.mainImageUrl,
                            contentDescription = post.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback icon if no image
                        Icon(
                            painter = painterResource(R.drawable.ic_map),
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostMap(
    posts: List<FirebasePost>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val montpellier = LatLng(43.6107, 3.8767)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(montpellier, 12f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        posts.forEachIndexed { index, post ->
            // Use the first step's location for the marker
            val firstStep = post.steps.firstOrNull()
            if (firstStep != null) {
                Marker(
                    state = MarkerState(position = LatLng(firstStep.latitude, firstStep.longitude)),
                    title = post.title,
                    snippet = post.locationName,
                    onClick = {
                        onItemClick(index)
                        false
                    }
                )
            }
        }
    }
}
