package fr.cestnous.travelwow

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp

data class Comment(
    val id: Int,
    val author: String,
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        val mockComments = remember {
            listOf(
                Comment(1, "Utilisateur 1", "Superbe parcours ! Les vues sont magnifiques.", 12),
                Comment(2, "Utilisateur 2", "Un peu difficile sur la fin, mais ça en vaut la peine.", 5),
                Comment(3, "Utilisateur 3", "J'ai adoré l'étape près de la rivière.", 8),
                Comment(4, "Utilisateur 4", "Parfait pour une rando en famille.", 3),
                Comment(5, "Utilisateur 5", "N'oubliez pas d'apporter de l'eau, il y a peu d'ombre.", 15),
                Comment(6, "Utilisateur 6", "Le sentier était un peu boueux, mais praticable.", 2),
                Comment(7, "Utilisateur 7", "Une expérience incroyable au lever du soleil !", 24),
                Comment(8, "Utilisateur 8", "Les indications sont très claires tout au long du trajet.", 7)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "Détails du parcours #$selectedItem",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val pagerState = rememberPagerState(pageCount = { 3 })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp
                    ) { page ->
                        Box(
                            modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Photo ${page + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(3) { index ->
                                        val color = if (pagerState.currentPage == index)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            Text(
                                text = "Ceci est une description générique du parcours sélectionné. " +
                                        "Ici apparaîtront les informations détaillées comme la durée, " +
                                        "la distance et les points d'intérêt.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Commentaires",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(mockComments, key = { it.id }) { comment ->
                            CommentItem(comment)
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
}

@Composable
fun CommentItem(comment: Comment) {
    var isLiked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(comment.initialLikes) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = {
                    if (isLiked) {
                        likesCount--
                    } else {
                        likesCount++
                    }
                    isLiked = !isLiked
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.ic_heart else R.drawable.ic_favorite),
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = likesCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
