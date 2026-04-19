package fr.cestnous.travelwow

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("TravelWowApp", "App Check Debug Provider installed")
        
        val storageBucket = FirebaseApp.getInstance().options.storageBucket
        Log.d("TravelWowApp", "Firebase initialized. Storage Bucket: $storageBucket")
    }
}
