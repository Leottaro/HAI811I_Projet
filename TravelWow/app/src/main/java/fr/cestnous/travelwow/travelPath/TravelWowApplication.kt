package fr.cestnous.travelwow.travelPath

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.cloudinary.android.MediaManager
import fr.cestnous.travelwow.BuildConfig

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Initialisation de Cloudinary
        MediaManager.init(this, mapOf(
            "cloud_name" to BuildConfig.cloudinarycloud_name,
            "secure" to true
        ))

        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("TravelWowApp", "App Check Debug Provider installed")
        
        val storageBucket = FirebaseApp.getInstance().options.storageBucket
        Log.d("TravelWowApp", "Firebase initialized. Storage Bucket: $storageBucket")
    }
}
