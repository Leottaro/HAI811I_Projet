package fr.cestnous.travelwow

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.UserProfile
import fr.cestnous.travelwow.data.repository.UserRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userRepository = UserRepository()
    val auth = FirebaseAuth.getInstance()

    // On enveloppe tout l'écran dans une Surface pour forcer le fond sombre
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TravelWow",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isLogin) "Connexion" else "Inscription",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            if (!isLogin) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Pseudo unique") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val cleanEmail = email.trim()
                    val cleanPassword = password.trim()
                    val cleanUsername = username.trim()

                    if (cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) {
                        if (isLogin) {
                            auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) onAuthSuccess()
                                    else Toast.makeText(context, "Identifiants incorrects", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            if (cleanUsername.isBlank()) {
                                Toast.makeText(context, "Veuillez choisir un pseudo", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            scope.launch {
                                if (userRepository.isUsernameUnique(cleanUsername)) {
                                    auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val user = task.result?.user
                                                if (user != null) {
                                                    scope.launch {
                                                        userRepository.createUserProfile(
                                                            UserProfile(uid = user.uid, username = cleanUsername, email = cleanEmail)
                                                        )
                                                        onAuthSuccess()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Erreur: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                } else {
                                    Toast.makeText(context, "Ce pseudo est déjà pris", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLogin) "Continuer" else "Créer mon compte")
            }

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(
                    text = if (isLogin) "Pas de compte ? S'inscrire" else "Déjà un compte ? Se connecter",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            OutlinedButton(
                onClick = {
                    auth.signInAnonymously().addOnCompleteListener { task ->
                        if (task.isSuccessful) onAuthSuccess()
                        else Toast.makeText(context, "Erreur mode anonyme", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Découvrir sans compte (Mode Anonyme)")
            }
        }
    }
}
