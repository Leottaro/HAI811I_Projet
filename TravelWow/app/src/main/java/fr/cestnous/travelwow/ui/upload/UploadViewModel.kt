package fr.cestnous.travelwow.ui.upload

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class UploadViewModel : ViewModel() {

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

    fun uploadPhotos(
        context: Context,
        uris: List<Uri>,
        description: String,
        locationName: String,
        placeType: String,
        tags: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isUploading.value = true
        val uploadedUrls = mutableListOf<String>()
        var uploadCount = 0

        viewModelScope.launch {
            val coords = getCoordinatesFromAddress(context, locationName)

            if (uris.isEmpty()) {
                _isUploading.value = false
                onError("Veuillez sélectionner au moins une photo")
                return@launch
            }

            uris.forEach { uri ->
                MediaManager.get().upload(uri)
                    .unsigned("travelWowPreset")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String?) {}
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {}

                        override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                            val imageUrl = resultData?.get("secure_url") as String
                            uploadedUrls.add(imageUrl)
                            uploadCount++

                            if (uploadCount == uris.size) {
                                savePhotoToFirestore(
                                    null, // Nouveau document
                                    uploadedUrls, description, locationName,
                                    coords.first, coords.second,
                                    placeType, tags, onSuccess, onError
                                )
                            }
                        }

                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            _isUploading.value = false
                            onError(error?.description ?: "Erreur Cloudinary")
                        }
                    }).dispatch()
            }
        }
    }

    fun updatePublication(
        context: Context,
        photoId: String,
        description: String,
        locationName: String,
        placeType: String,
        tags: List<String>,
        existingUrls: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isUploading.value = true
        viewModelScope.launch {
            val coords = getCoordinatesFromAddress(context, locationName)
            savePhotoToFirestore(
                photoId, existingUrls, description, locationName,
                coords.first, coords.second,
                placeType, tags, onSuccess, onError
            )
        }
    }

    private suspend fun getCoordinatesFromAddress(context: Context, address: String): Pair<Double?, Double?> = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                Pair(addresses[0].latitude, addresses[0].longitude)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun savePhotoToFirestore(
        photoId: String?,
        imageUrls: List<String>,
        description: String,
        locationName: String,
        lat: Double?,
        lon: Double?,
        placeType: String,
        tags: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid
                val userProfile = if (uid != null) userRepository.getUserProfile(uid) else null
                
                val photoData = mutableMapOf(
                    "imageUrls" to imageUrls,
                    "description" to description,
                    "locationName" to locationName,
                    "latitude" to lat,
                    "longitude" to lon,
                    "placeType" to placeType,
                    "tags" to tags,
                    "isPublic" to true,
                    "authorName" to (userProfile?.username ?: auth.currentUser?.email ?: "Anonyme")
                )

                if (photoId == null) {
                    // Création
                    photoData["authorId"] = uid ?: "Unknown"
                    photoData["timestamp"] = com.google.firebase.Timestamp.now()
                    photoData["likesCount"] = 0
                    db.collection("photos").add(photoData).await()
                } else {
                    // Mise à jour
                    db.collection("photos").document(photoId).update(photoData as Map<String, Any>).await()
                }

                _isUploading.value = false
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                onError(e.message ?: "Erreur Firestore")
            }
        }
    }
}
