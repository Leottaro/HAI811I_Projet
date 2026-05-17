package fr.cestnous.travelwow.travelPath.ui

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

data class FirebaseDraft(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val steps: List<FirebaseStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Composable
fun DraftsGallery(
    userId: String,
    onDraftClick: (FirebaseDraft) -> Unit,
    draftDao: DraftDao,
    modifier: Modifier = Modifier
) {
    val db = remember { Firebase.firestore }
    var localDrafts by remember { mutableStateOf<List<LocalDraft>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var draftToDelete by remember { mutableStateOf<LocalDraft?>(null) }

    if (draftToDelete != null) {
        AlertDialog(
            onDismissRequest = { draftToDelete = null },
            title = { Text("Supprimer le brouillon") },
            text = { Text("Êtes-vous sûr de vouloir supprimer ce brouillon ?") },
            confirmButton = {
                TextButton(onClick = {
                    val draft = draftToDelete ?: return@TextButton
                    scope.launch {
                        try {
                            // Delete locally first
                            draftDao.deleteByDraftId(draft.id)
                            localDrafts = localDrafts.filter { it.id != draft.id }

                            // Try to delete from Firestore
                            db.collection("users").document(userId)
                                .collection("drafts").document(draft.id)
                                .delete()
                                .await()
                        } catch (e: Exception) {
                            Log.e("DraftsGallery", "Error deleting draft from Firestore", e)
                        } finally {
                            draftToDelete = null
                        }
                    }
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        
        // Load from local Room DB first
        localDrafts = draftDao.getDraftsForUser(userId)
        if (localDrafts.isNotEmpty()) {
            isLoading = false
        }

        try {
            // Push unsynced drafts to Firestore
            val unsynced = draftDao.getUnsyncedDrafts().filter { it.userId == userId }
            unsynced.forEach { local ->
                try {
                    val draftRef = db.collection("users").document(userId)
                        .collection("drafts").document(local.id)
                    
                    val type = object : TypeToken<List<TravelStep>>() {}.type
                    val steps: List<TravelStep> = Gson().fromJson(local.stepsJson, type) ?: emptyList()

                    val draftMap = hashMapOf(
                        "title" to local.title,
                        "description" to local.description,
                        "steps" to steps.map { step ->
                            hashMapOf(
                                "name" to step.name,
                                "category" to step.category,
                                "latitude" to step.latitude,
                                "longitude" to step.longitude,
                                "images" to step.images
                            )
                        },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    draftRef.set(draftMap).await()
                    draftDao.insertDraft(local.copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e("DraftsGallery", "Failed to sync local draft ${local.id}", e)
                }
            }

            // Pull from Firestore
            val snapshot = db.collection("users").document(userId)
                .collection("drafts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val remoteDrafts = snapshot.documents.map { doc ->
                val stepsData = doc.get("steps") as? List<Map<String, Any>> ?: emptyList()
                val steps = stepsData.map { stepMap ->
                    FirebaseStep(
                        name = stepMap["name"] as? String ?: "",
                        category = stepMap["category"] as? String ?: "",
                        latitude = stepMap["latitude"] as? Double ?: 0.0,
                        longitude = stepMap["longitude"] as? Double ?: 0.0,
                        imageUrls = stepMap["images"] as? List<String> ?: emptyList()
                    )
                }
                FirebaseDraft(
                    id = doc.id,
                    title = doc.getString("title") ?: "Sans titre",
                    description = doc.getString("description") ?: "",
                    steps = steps,
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            // Sync remote drafts to local DB
            remoteDrafts.forEach { remote ->
                draftDao.insertDraft(LocalDraft.fromFirebaseDraft(remote, userId, isSynced = true))
            }

            // Refresh list from local DB to include both synced and unsynced
            localDrafts = draftDao.getDraftsForUser(userId)

        } catch (e: Exception) {
            Log.e("DraftsGallery", "Error fetching drafts from Firestore", e)
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (localDrafts.isEmpty()) {
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
            items(localDrafts) { localDraft ->
                val firebaseDraft = localDraft.toFirebaseDraft()
                DraftItem(
                    draft = firebaseDraft,
                    isSynced = localDraft.isSynced,
                    onClick = { onDraftClick(firebaseDraft) },
                    onDelete = { draftToDelete = localDraft }
                )
            }
        }
    }
}

@Composable
fun DraftItem(draft: FirebaseDraft, isSynced: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!isSynced) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = "Non synchronisé",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text("${draft.steps.size} étape(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Icon(painterResource(R.drawable.ic_return), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
