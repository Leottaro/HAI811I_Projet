package fr.cestnous.travelwow

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsBottomSheet(
    post: FirebasePost?,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    currentUserProfile: FirebaseUser? = null
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var otherReason by remember { mutableStateOf("") }
    val reportReasons = listOf("Contenu inapproprié", "Spam", "Fausse information", "Autre")

    var showUserDialog by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var showCommentDialog by remember { mutableStateOf(false) }

    val db = remember { Firebase.firestore }
    val auth = remember { Firebase.auth }
    val currentUser = auth.currentUser
    Log.d("DetailsBottomSheet", "Current user: $currentUser")
    var newCommentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var steps by remember { mutableStateOf<List<FirebaseStep>>(emptyList()) }
    var previewComments by remember { mutableStateOf<List<FirebaseComment>>(emptyList()) }

    LaunchedEffect(post?.id) {
        if (post != null) {
            try {
                // Fetch steps
                val stepsSnapshot = db.collection("travelpath_posts").document(post.id)
                    .collection("steps")
                    .orderBy("order")
                    .get()
                    .await()
                steps = stepsSnapshot.toObjects(FirebaseStep::class.java)

                // Fetch a preview of comments (last 3)
                db.collection("travelpath_posts").document(post.id)
                    .collection("comments")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
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
                        // Logique de signalement
                        showReportDialog = false
                    },
                    enabled = selectedReason != null
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
            onDismiss = { showCommentDialog = false },
            currentUserProfile = currentUserProfile
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${post.locationName} • ${post.distanceKm} km • ${post.durationMinutes / 60}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        item {
                            val allImages = steps.flatMap { it.imageUrls }
                            val pagerState = rememberPagerState(pageCount = { allImages.size.coerceAtLeast(1) })
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
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
                                                model = allImages[page],
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
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
                            CommentItem(
                                comment = comment,
                                onAuthorClick = {
                                    Log.d("DetailsBottomSheet", "Author clicked: ${comment.authorName} (ID: ${comment.authorId})")
                                    selectedUserId = comment.authorId
                                    showUserDialog = true
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = "Signaler",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Quick Comment Input
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp
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
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ajouter un commentaire...", style = MaterialTheme.typography.bodyMedium) },
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newCommentText.isNotBlank() && currentUser != null) {
                                    isSending = true
                                    val commentData = hashMapOf(
                                        "authorId" to currentUser.uid,
                                        "authorName" to (currentUserProfile?.username ?: currentUser.displayName ?: "Voyageur"),
                                        "authorPhotoUrl" to (currentUserProfile?.photoUrl ?: currentUser.photoUrl?.toString()),
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
        }
    }
}

@Composable
fun StepItemView(step: FirebaseStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = step.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (step.description.isNotBlank()) {
                Text(text = step.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: FirebaseComment,
    onAuthorClick: () -> Unit = {}
) {
    var isLiked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(comment.likesCount) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (comment.authorPhotoUrl != null) {
            Log.d("comment.authorPhotoUrl", comment.authorPhotoUrl);
            AsyncImage(
                model = comment.authorPhotoUrl,
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
                    comment.authorName.take(1).uppercase(),
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
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                
                val date = comment.createdAt?.toDate() ?: java.util.Date()
                val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                Text(
                    text = sdf.format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
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
                    onClick = {
                        if (isLiked) likesCount-- else likesCount++
                        isLiked = !isLiked
                    },
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
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Répondre",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { /* Action */ }
                )
            }
        }
    }
}
