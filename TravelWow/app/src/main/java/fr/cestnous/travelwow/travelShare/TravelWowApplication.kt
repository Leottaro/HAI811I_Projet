package fr.cestnous.travelwow.travelShare

import android.app.Application
import android.preference.PreferenceManager
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp
import fr.cestnous.travelwow.BuildConfig
import org.osmdroid.config.Configuration

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Initialisation OpenStreetMap (nécessaire pour les tuiles)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        // Initialisation de Cloudinary
        try {
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
            if (cloudName.isNotEmpty()) {
                val config = mapOf<String, String>("cloud_name" to cloudName)
                MediaManager.init(this, config)
            }
        } catch (e: Exception) {
            // Déjà initialisé
        }
    }
}
