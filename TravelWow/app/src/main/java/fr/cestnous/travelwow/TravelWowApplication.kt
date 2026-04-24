package fr.cestnous.travelwow

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.storage.storage
import com.cloudinary.android.MediaManager

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Initialisation de Cloudinary
        MediaManager.init(this, mapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
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
