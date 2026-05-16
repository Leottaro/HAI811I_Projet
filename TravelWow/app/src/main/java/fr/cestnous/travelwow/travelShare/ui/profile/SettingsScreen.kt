package fr.cestnous.travelwow.travelShare.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Notifications", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            NotificationSettingItem(
                title = "Messages",
                description = "Recevoir une notification lors d'un nouveau message",
                checked = profile?.notifyMessages ?: true,
                onCheckedChange = { viewModel.updateNotificationSettings("notifyMessages", it) }
            )

            NotificationSettingItem(
                title = "Demandes d'amis",
                description = "Recevoir une notification pour les nouvelles demandes",
                checked = profile?.notifyFriendRequests ?: true,
                onCheckedChange = { viewModel.updateNotificationSettings("notifyFriendRequests", it) }
            )

            NotificationSettingItem(
                title = "Likes",
                description = "Recevoir une notification quand quelqu'un aime votre photo",
                checked = profile?.notifyLikes ?: true,
                onCheckedChange = { viewModel.updateNotificationSettings("notifyLikes", it) }
            )

            NotificationSettingItem(
                title = "Commentaires",
                description = "Recevoir une notification quand quelqu'un commente votre photo",
                checked = profile?.notifyComments ?: true,
                onCheckedChange = { viewModel.updateNotificationSettings("notifyComments", it) }
            )
        }
    }
}

@Composable
fun NotificationSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
