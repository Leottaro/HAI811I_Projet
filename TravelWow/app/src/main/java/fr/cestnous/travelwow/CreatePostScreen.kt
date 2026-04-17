package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TravelStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun CreatePostContent(
    title: String,
    onTitleChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    steps: List<TravelStep>,
    onAddStep: (TravelStep) -> Unit,
    onRemoveStep: (TravelStep) -> Unit,
    selectedImages: List<String>,
    modifier: Modifier = Modifier
) {
    var showAddStepDialog by remember { mutableStateOf(false) }

    if (showAddStepDialog) {
        AddStepDialog(
            onDismiss = { showAddStepDialog = false },
            onStepAdded = { step ->
                onAddStep(step)
                showAddStepDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ... (Photos Section remains the same)
        Text(
            text = "Photos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { /* TODO: Image Picker */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Ajouter une photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(selectedImages) { imageUri ->
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        // Title and Location
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Titre du parcours") },
            placeholder = { Text("Ex: Randonnée au Pic Saint-Loup") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Lieu") },
            placeholder = { Text("Montpellier, France") },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_localisation), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            placeholder = { Text("Racontez votre expérience...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Itinerary / Steps Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Étapes du parcours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${steps.size} étape(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        // List of added steps
        steps.forEach { step ->
            StepItem(step = step, onRemove = { onRemoveStep(step) })
        }

        Button(
            onClick = { showAddStepDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_pin), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ajouter une étape GPS")
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun StepItem(step: TravelStep, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = step.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = "Lat: ${"%.4f".format(step.latitude)}, Lon: ${"%.4f".format(step.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Supprimer", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddStepDialog(
    onDismiss: () -> Unit,
    onStepAdded: (TravelStep) -> Unit
) {
    var stepName by remember { mutableStateOf("") }
    // In a real app, these would come from a Map picker
    val mockLat = remember { (43..45).random() + Math.random() }
    val mockLon = remember { (3..5).random() + Math.random() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle étape") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Entrez le nom de l'étape. La position GPS sera récupérée via la carte.")
                OutlinedTextField(
                    value = stepName,
                    onValueChange = { stepName = it },
                    label = { Text("Nom de l'étape") },
                    placeholder = { Text("Ex: Belvédère") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_map), contentDescription = null, modifier = Modifier.size(40.dp))
                        Text("Simulateur de Carte", style = MaterialTheme.typography.labelMedium)
                        Text("Position: ${"%.4f".format(mockLat)}, ${"%.4f".format(mockLon)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStepAdded(TravelStep(name = stepName.ifBlank { "Étape sans nom" }, latitude = mockLat, longitude = mockLon))
                }
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
