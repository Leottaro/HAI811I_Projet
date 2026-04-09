package fr.cestnous.travelwow.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.data.model.UserProfile
import fr.cestnous.travelwow.data.repository.PhotoRepository
import fr.cestnous.travelwow.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val photoRepository: PhotoRepository = PhotoRepository()
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _userPhotos = MutableStateFlow<List<TravelPhoto>>(emptyList())
    val userPhotos: StateFlow<List<TravelPhoto>> = _userPhotos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val auth = FirebaseAuth.getInstance()

    fun loadProfile() {
        val uid = auth.currentUser?.uid ?: run {
            _profile.value = null
            _userPhotos.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            // Charger le profil utilisateur
            _profile.value = userRepository.getUserProfile(uid)
            
            // Observer les photos de l'utilisateur en temps réel
            photoRepository.getUserPhotosFlow(uid).collectLatest { photos ->
                _userPhotos.value = photos
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(username: String, bio: String, onComplete: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val currentProfile = _profile.value
            if (username != currentProfile?.username) {
                if (!userRepository.isUsernameUnique(username)) {
                    _isLoading.value = false
                    onComplete(Result.failure(Exception("Ce pseudo est déjà utilisé")))
                    return@launch
                }
            }

            val updates = mapOf("username" to username, "bio" to bio)
            val result = userRepository.updateProfile(uid, updates)
            if (result.isSuccess) {
                _profile.value = userRepository.getUserProfile(uid)
            }
            _isLoading.value = false
            onComplete(result)
        }
    }

    fun createInitialProfile(username: String, bio: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val newProfile = UserProfile(uid = user.uid, username = username, bio = bio, email = user.email ?: "")
            userRepository.createUserProfile(newProfile)
            loadProfile()
        }
    }

    fun uploadProfilePicture(uri: Uri, onComplete: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true

        MediaManager.get().upload(uri)
            .unsigned("travelWowPreset")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                    val imageUrl = resultData?.get("secure_url") as String
                    viewModelScope.launch {
                        userRepository.updateProfile(uid, mapOf("profileImageUrl" to imageUrl))
                        _profile.value = userRepository.getUserProfile(uid)
                        _isLoading.value = false
                        onComplete(Result.success(Unit))
                    }
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    _isLoading.value = false
                    onComplete(Result.failure(Exception(error?.description ?: "Erreur Cloudinary")))
                }
            }).dispatch()
    }
}
