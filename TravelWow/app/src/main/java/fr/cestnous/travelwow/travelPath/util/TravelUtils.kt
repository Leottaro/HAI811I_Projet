package fr.cestnous.travelwow.travelPath.util

import android.content.Context
import androidx.core.net.toUri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import fr.cestnous.travelwow.travelPath.data.FirebaseStep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

suspend fun uploadToCloudinary(uri: String, context: Context): String = suspendCancellableCoroutine { continuation ->
    MediaManager.get().upload(uri.toUri())
        .unsigned("travelWowPreset")
        .callback(object : UploadCallback {
            override fun onStart(requestId: String) {}
            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                val url = resultData["secure_url"] as? String ?: ""
                continuation.resume(url)
            }
            override fun onError(requestId: String, error: ErrorInfo) {
                continuation.resumeWithException(Exception(error.description))
            }
            override fun onReschedule(requestId: String, error: ErrorInfo) {}
        })
        .dispatch(context)
}

/**
 * Calculates the great-circle distance between two points in kilometers.
 * Uses the Haversine formula.
 */
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Earth's radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/**
 * Calculates the total distance of a path defined by a list of steps.
 */
fun calculateTotalDistance(steps: List<fr.cestnous.travelwow.travelPath.data.TravelStep>): Double {
    if (steps.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until steps.size - 1) {
        val s1 = steps[i]
        val s2 = steps[i + 1]
        total += calculateDistance(s1.latitude, s1.longitude, s2.latitude, s2.longitude)
    }
    return total
}
