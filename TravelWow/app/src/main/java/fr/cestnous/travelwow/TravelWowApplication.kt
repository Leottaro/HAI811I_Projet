package fr.cestnous.travelwow

import android.app.Application
import android.preference.PreferenceManager
import android.util.Log
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.Firebase
import org.osmdroid.config.Configuration

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Initialisation App Check (depuis travelPath)
        // Commenté car renvoie une erreur 403 (API non activée) qui peut bloquer les requêtes
        /*
        try {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d("TravelWowApp", "App Check Debug Provider installed")
        } catch (e: Exception) {
            Log.e("TravelWowApp", "App Check initialization failed", e)
        }
        */

        // Initialisation OpenStreetMap (depuis travelShare)
        try {
            Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            Log.e("TravelWowApp", "OSM initialization failed", e)
        }

        // Initialisation Cloudinary
        try {
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
            if (cloudName.isNotEmpty()) {
                val config = mapOf("cloud_name" to cloudName, "secure" to true)
                MediaManager.init(this, config)
            }
        } catch (e: Exception) {
            // Déjà initialisé ou erreur de config
        }
    }
}
