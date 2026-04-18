package fr.cestnous.travelwow

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Comment(
    val id: Int,
    val author: String,
    val authorId: String, // Added authorId
    val text: String,
    val initialLikes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsBottomSheet(
    selectedItem: Int?,
    onDismissRequest: () -> Unit,
    sheetState: SheetState
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var otherReason by remember { mutableStateOf("") }
    val reportReasons = listOf("Contenu inapproprié", "Spam", "Fausse information", "Autre")

    var showUserDialog by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }

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
            onDismissRequest = {
                showReportDialog = false
                selectedReason = null
                otherReason = ""
            },
            title = { Text("Signaler le parcours") },
            text = {
                Column {
                    Text("Pourquoi souhaitez-vous signaler ce parcours ?")
                    Spacer(modifier = Modifier.height(8.dp))
                    reportReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedReason == reason),
                                onClick = { selectedReason = reason }
                            )
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    if (selectedReason == "Autre") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = otherReason,
                            onValueChange = { otherReason = it },
                            label = { Text("Précisez la raison") },
                            placeholder = { Text("Décrivez le problème...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                val isEnabled = selectedReason != null && (selectedReason != "Autre" || otherReason.isNotBlank())
                TextButton(
                    onClick = {
                        // TODO: Implement report submission logic
                        showReportDialog = false
                        selectedReason = null
                        otherReason = ""
                    },
                    enabled = isEnabled
                ) {
                    Text("Signaler", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportDialog = false
                    selectedReason = null
                    otherReason = ""
                }) {
                    Text("Annuler")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        val mockComments = remember {
            listOf(
                Comment(1, "Utilisateur 1", "user1_id", "Superbe parcours ! Les vues sont magnifiques.", 12),
                Comment(2, "Utilisateur 2", "user2_id", "Un peu difficile sur la fin, mais ça en vaut la peine.", 5),
                Comment(3, "Utilisateur 3", "user3_id", "J'ai adoré l'étape près de la rivière.", 8),
                Comment(4, "Utilisateur 4", "user4_id", "Parfait pour une rando en famille.", 3),
                Comment(5, "Utilisateur 5", "user5_id", "N'oubliez pas d'apporter de l'eau, il y a peu d'ombre.", 15),
                Comment(6, "Utilisateur 6", "user6_id", "Le sentier était un peu boueux, mais praticable.", 2),
                Comment(7, "Utilisateur 7", "user7_id", "Une expérience incroyable au lever du soleil !", 24),
                Comment(8, "Utilisateur 8", "user8_id", "Les indications sont très claires tout au long du trajet.", 7)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aventures à Chamonix", // Example title
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Parcours #$selectedItem • 12 km • 4h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                item {
                    val pagerState = rememberPagerState(pageCount = { 3 })
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_map),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    "Photo ${page + 1}", 
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Custom Indicator
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
                            repeat(3) { index ->
                                val active = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 10.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                        .align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Une randonnée exceptionnelle offrant des panoramas à couper le souffle sur le massif du Mont-Blanc. Le sentier est bien balisé mais demande une bonne condition physique pour la dernière ascension.",
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Commentaires",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "8 avis",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                items(mockComments, key = { it.id }) { comment ->
                    CommentItem(
                        comment = comment,
                        onAuthorClick = {
                            selectedUserId = comment.authorId
                            showUserDialog = true
                        }
                    )
                }
            }

            // Report button in the upper-right corner
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
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    onAuthorClick: () -> Unit = {}
) {
    var isLiked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(comment.initialLikes) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { onAuthorClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                comment.author.take(1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                Text(
                    text = "Il y a 2j",
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
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
