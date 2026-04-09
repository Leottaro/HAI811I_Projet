package fr.cestnous.travelwow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.ui.theme.TravelWowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelWowTheme {
                val auth = FirebaseAuth.getInstance()
                // On utilise un état local pour suivre l'utilisateur
                var currentUser by remember { mutableStateOf(auth.currentUser) }

                if (currentUser == null) {
                    AuthScreen(onAuthSuccess = {
                        currentUser = auth.currentUser
                    })
                } else {
                    // IMPORTANT : key(currentUser.uid) force la recréation de tout l'écran 
                    // si l'utilisateur change, ce qui vide les anciens ViewModels et états.
                    key(currentUser?.uid) {
                        TravelWowApp(onLogout = {
                            auth.signOut()
                            currentUser = null
                        })
                    }
                }
            }
        }
    }
}
