package fr.cestnous.travelwow

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

data class FirebaseDraft(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val steps: List<FirebaseStep> = emptyList()
)

@Composable
fun DraftsGallery(
    userId: String,
    onDraftClick: (FirebaseDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { Firebase.firestore }
    var drafts by remember { mutableStateOf<List<FirebaseDraft>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        try {
            val snapshot = db.collection("travelpath").document(userId)
                .collection("drafts")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            drafts = snapshot.documents.map { doc ->
                val stepsData = doc.get("steps") as? List<Map<String, Any>> ?: emptyList()
                val steps = stepsData.map { stepMap ->
                    FirebaseStep(
                        name = stepMap["name"] as? String ?: "",
                        latitude = stepMap["latitude"] as? Double ?: 0.0,
                        longitude = stepMap["longitude"] as? Double ?: 0.0,
                        imageUrls = stepMap["images"] as? List<String> ?: emptyList()
                    )
                }
                FirebaseDraft(
                    id = doc.id,
                    title = doc.getString("title") ?: "Sans titre",
                    description = doc.getString("description") ?: "",
                    steps = steps
                )
            }
        } catch (e: Exception) {
            Log.e("DraftsGallery", "Error fetching drafts", e)
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (drafts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("Aucun brouillon enregistré", color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(drafts) { draft ->
                DraftItem(draft = draft, onClick = { onDraftClick(draft) })
            }
        }
    }
}

@Composable
fun DraftItem(draft: FirebaseDraft, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(draft.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${draft.steps.size} étape(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(painterResource(R.drawable.ic_return), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
