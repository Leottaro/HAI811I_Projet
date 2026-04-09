package fr.cestnous.travelwow

import android.app.Application
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Initialisation de Cloudinary via BuildConfig (sécurisé via local.properties)
        // On utilise reflection ou on attend que Gradle génère le fichier. 
        // Pour éviter l'erreur de compilation si Gradle n'a pas encore fini, on peut aussi lire local.properties directement ici ou hardcoder temporairement pour tester.
        
        val cloudName = try {
            val buildConfigClass = Class.forName("${packageName}.BuildConfig")
            val field = buildConfigClass.getField("CLOUDINARY_CLOUD_NAME")
            field.get(null) as String
        } catch (e: Exception) {
            ""
        }
        
        if (cloudName.isNotEmpty()) {
            val config = mapOf("cloud_name" to cloudName)
            try {
                MediaManager.init(this, config)
            } catch (e: Exception) {
                // Déjà initialisé
            }
        }
    }
}
