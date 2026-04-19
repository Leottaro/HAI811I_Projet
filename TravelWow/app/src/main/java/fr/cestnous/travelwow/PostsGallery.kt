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
    onPostClick: (FirebasePost) -> Unit,
    viewMode: GalleryViewMode,
    modifier: Modifier = Modifier,
    userIdFilter: String? = null
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
                var query = db.collection("travelpath").document("posts").collection("posts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                
                if (userIdFilter != null) {
                    query = query.whereEqualTo("authorId", userIdFilter)
                }

                val snapshot = query.limit(50).get().await()
                
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
                    onPostClick = onPostClick
                )
            }
            GalleryViewMode.MAP -> {
                PostMap(
                    posts = posts,
                    onPostClick = onPostClick
                )
            }
        }
    }
}

@Composable
fun PostGrid(
    posts: List<FirebasePost>,
    onPostClick: (FirebasePost) -> Unit,
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
            itemsIndexed(posts, key = { _, post -> post.id }) { _, post ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPostClick(post) }
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
    onPostClick: (FirebasePost) -> Unit,
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
        posts.forEach { post ->
            Marker(
                state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                title = post.title,
                snippet = post.locationName,
                onClick = {
                    onPostClick(post)
                    false
                }
            )
        }
    }
}
