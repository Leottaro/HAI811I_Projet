package fr.cestnous.travelwow

import android.app.Application
import com.google.firebase.FirebaseApp

class TravelWowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
