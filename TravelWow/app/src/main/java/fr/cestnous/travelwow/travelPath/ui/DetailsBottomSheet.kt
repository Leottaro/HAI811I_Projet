package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheetContent(
    post: FirebasePost?,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    currentUserProfile: FirebaseUser? = null
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTargetId by remember { mutableStateOf<String?>(null) }
    var reportTargetType by remember { mutableStateOf("post") }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var otherReason by remember { mutableStateOf("") }
    val reportReasons = listOf("Contenu inapproprié", "Spam", "Fausse information", "Autre")

    var showUserDialog by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            // Small delay to let the initial layout pass complete
            delay(50)
            if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                sheetState.expand()
            }
        }
    }

    val db = remember { Firebase.firestore }
    var isFavorite by remember { mutableStateOf(false) }
    val auth = remember { Firebase.auth }
    val currentUser = auth.currentUser
    var newCommentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var author by remember { mutableStateOf<FirebaseUser?>(null) }
    LaunchedEffect(post?.authorId) {
        val authorId = post?.authorId
        if (!authorId.isNullOrBlank()) {
            try {
                val snapshot = db.collection("users").document(authorId).get().await()
                author = snapshot.toObject(FirebaseUser::class.java)
            } catch (e: Exception) {
                Log.e("DetailsBottomSheet", "Error fetching author", e)
            }
        }
    }

    LaunchedEffect(post?.id, currentUser?.uid) {
        if (post != null && currentUser != null) {
            try {
                // Check if the post ID is in the user's liked_posts sub-collection
                val likedPostDoc = db.collection("users").document(currentUser.uid)
                    .collection("liked_posts").document(post.id).get().await()
                isFavorite = likedPostDoc.exists()
                val likedAt = likedPostDoc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                
                // Update local cache
                val dbLocal = TravelWowDatabase.getDatabase(context)
                val existsInLocal = dbLocal.favoritePostDao().isFavorite(post.id)
                
                if (isFavorite && !existsInLocal) {
                    dbLocal.favoritePostDao().insertFavorite(FavoritePost.fromFirebasePost(post, likedAt))
                } else if (!isFavorite && existsInLocal) {
                    dbLocal.favoritePostDao().deleteByPostId(post.id)
                }
            } catch (e: Exception) {
                Log.e("DetailsBottomSheet", "Error checking favorites", e)
            }
        }
    }

    var steps by remember { mutableStateOf<List<FirebaseStep>>(emptyList()) }
    var previewComments by remember { mutableStateOf<List<FirebaseComment>>(emptyList()) }
    var isStepsLoading by remember { mutableStateOf(false) }

    val allImages = remember(post, steps) {
        val stepImages = steps.flatMap { it.imageUrls }
        if (stepImages.isEmpty() && post?.mainImageUrl != null) {
            listOf(post.mainImageUrl)
        } else {
            stepImages
        }
    }
    val pagerState = key(post?.id) {
        rememberPagerState(
            pageCount = { allImages.size.coerceAtLeast(1) }
        )
    }

    LaunchedEffect(post?.id) {
        if (post != null) {
            isStepsLoading = true
            try {
                // Fetch steps
                val stepsSnapshot = db.collection("travelpath_posts").document(post.id)
                    .collection("steps")
                    .orderBy("order")
                    .get()
                    .await()
                steps = stepsSnapshot.toObjects(FirebaseStep::class.java)
                Log.d("DetailsBottomSheet", "Fetched ${steps.size} steps for post ${post.id}")
                steps.forEachIndexed { index, step ->
                    Log.d("DetailsBottomSheet", "Step $index: ${step.name}, Images: ${step.imageUrls.size}")
                }
                isStepsLoading = false

                // Fetch a preview of comments (last 3)
                db.collection("travelpath_posts").document(post.id)
                    .collection("comments")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(3)
                    .addSnapshotListener { snapshot, e ->
                        if (snapshot != null) {
                            previewComments = snapshot.toObjects(FirebaseComment::class.java)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            steps = emptyList()
            previewComments = emptyList()
        }
    }

    if (showUserDialog && selectedUserId != null) {
        UserDetailDialog(
            userId = selectedUserId!!,
            onDismiss = {
                showUserDialog = false
                selectedUserId = null
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Signaler le contenu") },
            text = {
                Column {
                    reportReasons.forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedReason == reason),
                                onClick = { selectedReason = reason }
                            )
                            Text(text = reason, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (selectedReason == "Autre") {
                        OutlinedTextField(
                            value = otherReason,
                            onValueChange = { otherReason = it },
                            label = { Text("Précisez la raison") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (post != null && currentUser != null) {
                            val report = FirebaseReport(
                                reporterId = currentUser.uid,
                                targetId = reportTargetId ?: post.id,
                                targetType = reportTargetType,
                                reason = selectedReason ?: "Non spécifié",
                                otherReason = if (selectedReason == "Autre") otherReason else null
                            )
                            db.collection("reports").add(report)
                                .addOnSuccessListener {
                                    Log.d("DetailsBottomSheet", "Report sent successfully")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("DetailsBottomSheet", "Error sending report", e)
                                }
                        }
                        showReportDialog = false
                        selectedReason = null
                        otherReason = ""
                    },
                    enabled = selectedReason != null && (selectedReason != "Autre" || otherReason.isNotBlank())
                ) {
                    Text("Signaler")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showCommentDialog && post != null) {
        CommentDialog(
            postId = post.id,
            postAuthorId = post.authorId,
            postTitle = post.title,
            onDismiss = { showCommentDialog = false },
            currentUserProfile = currentUserProfile
        )
    }

    if (showShareDialog && post != null) {
        SharePostDialog(
            post = post,
            onDismiss = { showShareDialog = false },
            currentUserProfile = currentUserProfile
        )
    }

    if (showDeleteDialog && post != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le parcours") },
            text = { Text("Êtes-vous sûr de vouloir supprimer ce parcours ? Cette action est irréversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        coroutineScope.launch {
                            try {
                                // Delete steps sub-collection
                                val stepsSnapshot = db.collection("travelpath_posts").document(post.id)
                                    .collection("steps").get().await()
                                stepsSnapshot.documents.forEach { it.reference.delete() }

                                // Delete comments sub-collection
                                val commentsSnapshot = db.collection("travelpath_posts").document(post.id)
                                    .collection("comments").get().await()
                                commentsSnapshot.documents.forEach { it.reference.delete() }

                                // Delete the post document
                                db.collection("travelpath_posts").document(post.id).delete().await()

                                isDeleting = false
                                showDeleteDialog = false
                                onDismissRequest() // Close the bottom sheet
                            } catch (e: Exception) {
                                Log.e("DetailsBottomSheet", "Error deleting post", e)
                                isDeleting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Supprimer")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                    Text("Annuler")
                }
            }
        )
    }

    if (post == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Internal Scaffold for the content (to have the bottomBar comment input)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    // Quick Comment Input
                    Surface(
                        tonalElevation = 2.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .navigationBarsPadding()
                                .imePadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                sheetState.expand()
                                            }
                                        }
                                    },
                                placeholder = {
                                    Text(
                                        "Ajouter un commentaire...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    ),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    )
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newCommentText.isNotBlank() && currentUser != null) {
                                        isSending = true
                                        val commentData = hashMapOf(
                                            "authorId" to currentUser.uid,
                                            "text" to newCommentText,
                                            "likesCount" to 0,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )

                                        db.collection("travelpath_posts").document(post.id)
                                            .collection("comments")
                                            .add(commentData)
                                            .addOnSuccessListener {
                                                newCommentText = ""
                                                isSending = false
                                                // Update comments count in post
                                                db.collection("travelpath_posts").document(post.id)
                                                    .update("commentsCount", FieldValue.increment(1))
                                                
                                                // Send Notification to Post Author (if not self)
                                                if (post.authorId.isNotBlank() && post.authorId != currentUser.uid) {
                                                    coroutineScope.launch {
                                                        try {
                                                            val authorDoc = db.collection("users").document(post.authorId).get().await()
                                                            val authorProfile = authorDoc.toObject(FirebaseUser::class.java)
                                                            
                                                            if (authorProfile?.settings?.commentsNotifications == true) {
                                                                val senderName = currentUserProfile?.username ?: currentUser.displayName ?: "Un voyageur"
                                                                val notification = FirebaseNotification(
                                                                    recipientId = post.authorId,
                                                                    senderId = currentUser.uid,
                                                                    senderName = senderName,
                                                                    senderPhotoUrl = currentUserProfile?.photoUrl ?: currentUser.photoUrl?.toString(),
                                                                    type = NotificationType.COMMENT,
                                                                    targetId = post.id,
                                                                    title = "Nouveau commentaire !",
                                                                    message = "$senderName a commenté votre parcours \"${post.title}\"."
                                                                )
                                                                db.collection("notifications").add(notification)
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("DetailsBottomSheet", "Error sending comment notification", e)
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("DetailsBottomSheet", "Error adding comment", e)
                                                isSending = false
                                            }
                                    }
                                },
                                enabled = newCommentText.isNotBlank() && !isSending,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (newCommentText.isNotBlank() && !isSending)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Envoyer",
                                        tint = if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = innerPadding.calculateBottomPadding() + 16.dp,
                            top = 0.dp
                        )
                    ) {
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            selectedUserId = post.authorId
                                            showUserDialog = true
                                        }
                                        .padding(bottom = 12.dp)
                                ) {
                                    if (author?.photoUrl != null) {
                                        AsyncImage(
                                            model = author?.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (author != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                author!!.username.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = author?.username ?: "Username",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Créateur du parcours",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${post.locationName} • ${"%.1f".format(post.distanceKm)} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                if (post.categories.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        post.categories.forEach { category ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = category,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                if (isStepsLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                } else {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        pageSpacing = 12.dp
                                    ) { page ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (allImages.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(allImages[page])
                                                        .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    onError = { error ->
                                                        Log.e("DetailsBottomSheet", "Error loading image at page $page (${allImages[page]}): ${error.result.throwable}")
                                                    }
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_map),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (allImages.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 16.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                    CircleShape
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            repeat(allImages.size) { index ->
                                                val active = pagerState.currentPage == index
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (active) 10.dp else 6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (active) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.3f
                                                            )
                                                        )
                                                        .align(Alignment.CenterVertically)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                if(post.description != null) {
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = post.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                                
                                if (steps.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Étapes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    steps.forEach { step ->
                                        StepItemView(step)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCommentDialog = true }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Commentaires",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${post.commentsCount} avis",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        items(previewComments, key = { it.id }) { comment ->
                            var commentAuthor by remember { mutableStateOf<FirebaseUser?>(null) }
                            LaunchedEffect(comment.authorId) {
                                if (comment.authorId.isNotBlank()) {
                                    try {
                                        val snapshot = db.collection("users").document(comment.authorId).get().await()
                                        commentAuthor = snapshot.toObject(FirebaseUser::class.java)
                                    } catch (e: Exception) {
                                        Log.e("DetailsBottomSheet", "Error fetching comment author", e)
                                    }
                                }
                            }

                            if (commentAuthor != null) {
                                CommentItem(
                                    comment = comment,
                                    author = commentAuthor!!,
                                    currentUserId = currentUser?.uid,
                                    onLikeClick = {
                                        if (currentUser != null) {
                                            val commentRef = db.collection("travelpath_posts")
                                                .document(post.id)
                                                .collection("comments")
                                                .document(comment.id)

                                            coroutineScope.launch {
                                                try {
                                                    if (comment.likedByUsers.contains(currentUser.uid)) {
                                                        commentRef.update(
                                                            "likedByUsers",
                                                            FieldValue.arrayRemove(currentUser.uid)
                                                        ).await()
                                                    } else {
                                                        commentRef.update(
                                                            "likedByUsers",
                                                            FieldValue.arrayUnion(currentUser.uid)
                                                        ).await()
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "DetailsBottomSheet",
                                                        "Error liking comment",
                                                        e
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onAuthorClick = {
                                        Log.d(
                                            "DetailsBottomSheet",
                                            "Author clicked: ${commentAuthor!!.username} (ID: ${comment.authorId})"
                                        )
                                        selectedUserId = comment.authorId
                                        showUserDialog = true
                                    },
                                    onReportClick = {
                                        reportTargetId = comment.id
                                        reportTargetType = "comment"
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Floating Close Button at Top-Left
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val isAuthor = currentUser?.uid == post.authorId

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    val file = PdfExporter.exportPostToPdf(context, post, author!!, steps)
                                    isExporting = false
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Ouvrir le PDF"))
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = "Exporter en PDF",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Partager",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isAuthor) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else {
                            IconButton(
                                onClick = {
                                    if (currentUser != null) {
                                        val userRef = db.collection("users").document(currentUser.uid)
                                        val postRef = db.collection("travelpath_posts").document(post.id)
                                        val likedPostRef = userRef.collection("liked_posts").document(post.id)
                                        val dbLocal = TravelWowDatabase.getDatabase(context)
                                        
                                        coroutineScope.launch {
                                            try {
                                                if (isFavorite) {
                                                    // Unlike
                                                    likedPostRef.delete().await()
                                                    postRef.update("likesCount", FieldValue.increment(-1)).await()
                                                    dbLocal.favoritePostDao().deleteByPostId(post.id)
                                                    isFavorite = false
                                                } else {
                                                    // Like
                                                    val now = System.currentTimeMillis()
                                                    likedPostRef.set(FirebaseLikedPost(id = post.id)).await()
                                                    postRef.update("likesCount", FieldValue.increment(1)).await()
                                                    dbLocal.favoritePostDao().insertFavorite(FavoritePost.fromFirebasePost(post, now))
                                                    isFavorite = true

                                                    // Send Notification to Post Author (if not self)
                                                    if (post.authorId.isNotBlank() && post.authorId != currentUser.uid) {
                                                        try {
                                                            val authorDoc = db.collection("users").document(post.authorId).get().await()
                                                            val authorProfile = authorDoc.toObject(FirebaseUser::class.java)
                                                            
                                                            if (authorProfile?.settings?.likesNotifications == true) {
                                                                val senderName = currentUserProfile?.username ?: currentUser.displayName ?: "Un voyageur"
                                                                val notification = FirebaseNotification(
                                                                    recipientId = post.authorId,
                                                                    senderId = currentUser.uid,
                                                                    senderName = senderName,
                                                                    senderPhotoUrl = currentUserProfile?.photoUrl ?: currentUser.photoUrl?.toString(),
                                                                    type = NotificationType.LIKE,
                                                                    targetId = post.id,
                                                                    title = "Nouveau like !",
                                                                    message = "$senderName a aimé votre parcours \"${post.title}\"."
                                                                )
                                                                db.collection("notifications").add(notification)
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("DetailsBottomSheet", "Error sending like notification", e)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("DetailsBottomSheet", "Error updating liked posts", e)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite),
                                    contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { 
                                    reportTargetId = post.id
                                    reportTargetType = "post"
                                    showReportDialog = true 
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_warning),
                                    contentDescription = "Signaler",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepItemView(step: FirebaseStep) {
    val categoryIcon = when (step.category) {
        "Restauration" -> Icons.Default.Restaurant
        "Loisirs" -> Icons.Default.Hiking
        "Découvertes" -> Icons.Default.Explore
        "Culture" -> Icons.Default.AccountBalance
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (categoryIcon != null) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = step.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (step.category.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = step.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (step.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = step.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (step.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(step.imageUrls) { imageUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .setHeader("User-Agent", "TravelWowApp/1.0 (https://github.com/leo/TravelWow; travelwow-app@example.com)")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            onError = { error ->
                                Log.e("DetailsBottomSheet", "Error loading step image ($imageUrl): ${error.result.throwable}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: FirebaseComment,
    author: FirebaseUser,
    currentUserId: String? = null,
    onLikeClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    val isLiked = currentUserId != null && comment.likedByUsers.contains(currentUserId)
    val likesCount = comment.likedByUsers.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (author.photoUrl != null) {
            AsyncImage(
                model = author.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onAuthorClick() },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onAuthorClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    author.username.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = author.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val date = comment.createdAt.toDate()
                    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    Text(
                        text = sdf.format(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.ic_heart else R.drawable.ic_favorite),
                        contentDescription = null,
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likesCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
