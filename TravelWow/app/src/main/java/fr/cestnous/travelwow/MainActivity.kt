package fr.cestnous.travelwow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.ui.theme.TravelWowTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted.
        } else {
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied.
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        setContent {
            TravelWowTheme {
                var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

                if (currentUser == null) {
                    AuthScreen(onAuthSuccess = {
                        currentUser = FirebaseAuth.getInstance().currentUser
                    })
                } else {
                    TravelWowApp(
                        user = currentUser!!,
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            currentUser = null
                        }
                    )
                }
            }
        }
    }
}
