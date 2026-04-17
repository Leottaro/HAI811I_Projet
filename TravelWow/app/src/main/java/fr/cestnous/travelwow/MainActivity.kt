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
