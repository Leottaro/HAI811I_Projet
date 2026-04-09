package fr.cestnous.travelwow.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.data.model.TravelPhoto
import fr.cestnous.travelwow.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: PhotoRepository = PhotoRepository()) : ViewModel() {

    private val _photos = MutableStateFlow<List<TravelPhoto>>(emptyList())
    val photos: StateFlow<List<TravelPhoto>> = _photos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val auth = FirebaseAuth.getInstance()

    init {
        observePhotos()
    }

    private fun observePhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getPhotosFlow().collectLatest { 
                _photos.value = it
                _isLoading.value = false
            }
        }
    }

    fun searchPhotos(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                observePhotos()
            } else {
                _isLoading.value = true
                _photos.value = repository.searchPhotos(query)
                _isLoading.value = false
            }
        }
    }

    fun filterByPlaceType(type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val all = repository.getPublicPhotos()
            _photos.value = if (type == "Tous") all else all.filter { it.placeType == type }
            _isLoading.value = false
        }
    }

    fun toggleLike(photoId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.toggleLike(photoId, userId)
        }
    }
}
