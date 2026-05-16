package fr.cestnous.travelwow.travelShare.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesViewModel(private val repository: PhotoRepository = PhotoRepository()) : ViewModel() {

    private val _favoritePhotos = MutableStateFlow<List<TravelPhoto>>(emptyList())
    val favoritePhotos: StateFlow<List<TravelPhoto>> = _favoritePhotos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val auth = FirebaseAuth.getInstance()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            // On observe toutes les photos et on filtre celles que l'utilisateur a aimées
            repository.getPhotosFlow().collectLatest { allPhotos ->
                _favoritePhotos.value = allPhotos.filter { it.likedBy.contains(userId) }
                _isLoading.value = false
            }
        }
    }
}
