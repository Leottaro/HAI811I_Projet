package fr.cestnous.travelwow

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.firestore.Query
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
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
    userIdFilter: String? = null,
    excludeUserId: String? = null,
    searchQuery: String = "",
    favoritesUserId: String? = null,
    filter: PostFilter = PostFilter(),
    focusedPost: FirebasePost? = null,
    onFocusedPostChange: (FirebasePost?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onEmptySpaceClick: () -> Unit = {}
) {
    val db = remember { Firebase.firestore }
    val context = LocalContext.current
    var posts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val fetchPosts = {
        coroutineScope.launch {
            isRefreshing = true
            try {
                Log.d("PostsGallery", "Fetching posts... mode: $viewMode")
                
                val limitCount = 50

                if (favoritesUserId != null) {
                    Log.d("PostsGallery", "Fetching favorites for: $favoritesUserId")
                    
                    // Load from local cache first
                    val dbLocal = TravelWowDatabase.getDatabase(context)
                    val cachedFavorites = dbLocal.favoritePostDao().getAllFavorites()
                    if (cachedFavorites.isNotEmpty()) {
                        posts = cachedFavorites.map { it.toFirebasePost() }
                    }

                    // Fetch liked post IDs from the sub-collection, sorted by like date (createdAt)
                    var likedQuery = db.collection("users").document(favoritesUserId)
                        .collection("liked_posts")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                    
                    if (viewMode == GalleryViewMode.GRID) {
                        likedQuery = likedQuery.limit(limitCount.toLong())
                    }

                    val likedPostsSnapshot = likedQuery.get().await()
                    val likedPostIdsWithDate = likedPostsSnapshot.documents.map { 
                        it.id to (it.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                    }
                    val likedPostIds = likedPostIdsWithDate.map { it.first }

                    if (likedPostIds.isEmpty()) {
                        posts = emptyList()
                        coroutineScope.launch {
                            dbLocal.favoritePostDao().clearAll()
                        }
                    } else {
                        // Fetch posts by IDs
                        // Firestore whereIn has a limit of 30 items
                        val firestorePosts = mutableListOf<FirebasePost>()
                        likedPostIds.chunked(30).forEach { chunk ->
                            val snapshot = db.collection("travelpath_posts")
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get()
                                .await()
                            firestorePosts.addAll(snapshot.toObjects(FirebasePost::class.java))
                        }

                        // Maintain the order of likedPostIds and attach likedAt
                        val postWithLikes = likedPostIdsWithDate.mapNotNull { (id, likedAt) ->
                            firestorePosts.find { it.id == id }?.let { it to likedAt }
                        }

                        var filteredPosts = postWithLikes.map { it.first }

                        // Apply filters
                        if (filter.selectedCategories.isNotEmpty()) {
                            filteredPosts = filteredPosts.filter { it.categories.toSet().containsAll(filter.selectedCategories) }
                        }
                        filteredPosts = filteredPosts.filter { it.distanceKm >= filter.minDistance && it.distanceKm <= filter.maxDistance }

                        posts = filteredPosts
                        
                        coroutineScope.launch {
                            // Sync cache with Firestore data
                            val firestoreIds = filteredPosts.map { it.id }.toSet()
                            
                            // Add/Update from Firestore
                            postWithLikes.forEach { (post, likedAt) ->
                                if (firestoreIds.contains(post.id)) {
                                    dbLocal.favoritePostDao().insertFavorite(FavoritePost.fromFirebasePost(post, likedAt))
                                }
                            }
                            
                            // Remove what's no longer in Firestore (among liked posts)
                            cachedFavorites.forEach { cached ->
                                if (!firestoreIds.contains(cached.id)) {
                                    dbLocal.favoritePostDao().deleteByPostId(cached.id)
                                }
                            }
                        }
                    }
                } else {
                    var query: Query = if (userIdFilter != null) {
                        Log.d("PostsGallery", "Filtering by userId: $userIdFilter")
                        db.collection("travelpath_posts").whereEqualTo("authorId", userIdFilter)
                    } else if (excludeUserId != null) {
                        Log.d("PostsGallery", "Excluding userId: $excludeUserId")
                        db.collection("travelpath_posts").whereNotEqualTo("authorId", excludeUserId)
                    } else {
                        db.collection("travelpath_posts")
                    }

                    if (viewMode == GalleryViewMode.GRID) {
                        query = query.limit(limitCount.toLong())
                    }

                    val snapshot = query.get().await()
                    Log.d("PostsGallery", "Snapshot received with ${snapshot.size()} documents")
                    var fetchedPosts = snapshot.toObjects(FirebasePost::class.java)
                    
                    // Apply filters
                    if (filter.selectedCategories.isNotEmpty()) {
                        fetchedPosts = fetchedPosts.filter { it.categories.toSet().containsAll(filter.selectedCategories) }
                    }
                    fetchedPosts = fetchedPosts.filter { it.distanceKm >= filter.minDistance && it.distanceKm <= filter.maxDistance }

                    // Sorting logic
                    fetchedPosts = when {
                        userIdFilter != null -> {
                            // Profile screen: Sort by creation date
                            fetchedPosts.sortedByDescending { it.createdAt }
                        }
                        viewMode == GalleryViewMode.GRID -> {
                            // Main screen (or Search) in Grid: Random sort
                            fetchedPosts.shuffled()
                        }
                        else -> {
                            // Map mode or others: default to date
                            fetchedPosts.sortedByDescending { it.createdAt }
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        fetchedPosts = fetchedPosts.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.description?.contains(searchQuery, ignoreCase = true) == true ||
                                    it.locationName.contains(searchQuery, ignoreCase = true) ||
                                    it.categories.any { it.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    posts = fetchedPosts
                }
            } catch (e: Exception) {
                Log.e("PostsGallery", "Error fetching posts", e)
                e.printStackTrace()
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(searchQuery, filter) {
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
                    onPostClick = onPostClick,
                    onEmptySpaceClick = onEmptySpaceClick,
                    isLoading = isRefreshing
                )
            }
            GalleryViewMode.MAP -> {
                PostMap(
                    posts = posts,
                    onPostClick = onPostClick,
                    focusedPost = focusedPost,
                    onFocusedPostChange = onFocusedPostChange,
                    contentPadding = contentPadding
                )
            }
        }
    }
}

@Composable
fun PostGrid(
    posts: List<FirebasePost>,
    onPostClick: (FirebasePost) -> Unit,
    modifier: Modifier = Modifier,
    onEmptySpaceClick: () -> Unit = {},
    isLoading: Boolean = false
) {
    if (isLoading && posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    } else if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onEmptySpaceClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Aucune publication trouvée",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onEmptySpaceClick() },
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
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(post.mainImageUrl)
                                .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                                .crossfade(true)
                                .build(),
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
    modifier: Modifier = Modifier,
    focusedPost: FirebasePost? = null,
    onFocusedPostChange: (FirebasePost?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val db = remember { Firebase.firestore }
    val montpellier = LatLng(43.6107, 3.8767)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(montpellier, 12f)
    }
    
    var focusedPostSteps by remember { mutableStateOf<List<FirebaseStep>>(emptyList()) }

    LaunchedEffect(focusedPost) {
        if (focusedPost != null) {
            try {
                val snapshot = db.collection("travelpath_posts")
                    .document(focusedPost.id)
                    .collection("steps")
                    .orderBy("order")
                    .get()
                    .await()
                focusedPostSteps = snapshot.toObjects(FirebaseStep::class.java)
            } catch (e: Exception) {
                Log.e("PostMap", "Error fetching steps", e)
            }
        } else {
            focusedPostSteps = emptyList()
        }
    }

    LaunchedEffect(focusedPost, focusedPostSteps) {
        if (focusedPost != null) {
            try {
                val builder = LatLngBounds.Builder()
                builder.include(LatLng(focusedPost.latitude, focusedPost.longitude))
                focusedPostSteps.forEach { step ->
                    builder.include(LatLng(step.latitude, step.longitude))
                }
                val bounds = builder.build()
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 150),
                    durationMs = 1000
                )
            } catch (e: Exception) {
                Log.e("PostMap", "Error zooming to bounds", e)
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = { onFocusedPostChange(null) },
        contentPadding = contentPadding
    ) {
        if (focusedPost == null) {
            posts.forEach { post ->
                Marker(
                    state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                    title = post.title,
                    snippet = post.locationName,
                    onClick = {
                        onFocusedPostChange(post)
                        onPostClick(post)
                        true // Return true to consume the click and not show default info window yet
                    }
                )
            }
        } else {
            // Show only the steps of the focused post
            focusedPostSteps.forEachIndexed { index, step ->
                Marker(
                    state = MarkerState(position = LatLng(step.latitude, step.longitude)),
                    title = "Étape ${index + 1}: ${step.name}",
                    snippet = step.description,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            
            // Optionally also show the main post marker in a different color
            Marker(
                state = MarkerState(position = LatLng(focusedPost.latitude, focusedPost.longitude)),
                title = focusedPost.title,
                snippet = "Départ",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                onClick = {
                    onPostClick(focusedPost)
                    false
                }
            )
        }
    }
}
