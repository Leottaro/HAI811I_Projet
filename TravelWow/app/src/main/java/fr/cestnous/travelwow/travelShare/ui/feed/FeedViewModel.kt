package fr.cestnous.travelwow.travelShare.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.data.repository.PhotoRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SortOrder {
    DATE_DESC, // Plus récent au plus ancien
    DATE_ASC,  // Plus ancien au plus récent
    AUTHOR_ASC // Ordre alphabétique des auteurs
}
class FeedViewModel(
    private val repository: PhotoRepository = PhotoRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private var allPhotosList: List<TravelPhoto> = emptyList()
    private val _photos = MutableStateFlow<List<TravelPhoto>>(emptyList())
    val photos: StateFlow<List<TravelPhoto>> = _photos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val auth = FirebaseAuth.getInstance()

    private var currentSortOrder = SortOrder.DATE_DESC

    init {
        observePhotos()
    }

    private fun observePhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getPhotosFlow().collectLatest {
                allPhotosList = it
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

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Rayon de la terre en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
    fun applyFilters(
        type: String? = null,
        authorName: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        radius: Double? = null,
        targetLat: Double? = null,
        targetLon: Double? = null,
        sortOrder: SortOrder = SortOrder.DATE_DESC
    ) {
        currentSortOrder = sortOrder
        // On filtre à partir de allPhotosList (la source complète)
        val filteredList = allPhotosList.filter { photo ->
            // 1. Filtre par type (Nature, Ville, etc.)
            val matchType = type == null || photo.placeType == type

            // 2. Filtre par nom d'auteur
            val matchAuthor = authorName == null || photo.authorName.contains(authorName, ignoreCase = true)

            // 3. Filtre par dates
            val photoDate = photo.timestamp.toDate().time
            val matchStart = startDate == null || photoDate >= startDate
            val matchEnd = endDate == null || photoDate <= endDate
            val matchLocation = if (radius != null && targetLat != null && targetLon != null) {
                if (photo.latitude != null && photo.longitude != null) {
                    val distance = calculateDistance(targetLat, targetLon, photo.latitude, photo.longitude)
                    distance <= radius
                } else false
            } else true

            // 4. Retourne vrai si tous les critères correspondent
            matchType && matchAuthor && matchStart && matchEnd && matchLocation
        }.let { list ->
            // 3. Appliquer le tri sur la liste filtrée
            when (currentSortOrder) {
                SortOrder.DATE_DESC -> list.sortedByDescending { it.timestamp }
                SortOrder.DATE_ASC -> list.sortedBy { it.timestamp }
                SortOrder.AUTHOR_ASC -> list.sortedBy { it.authorName.lowercase() }
            }
        }

        // On met à jour le StateFlow pour que l'UI se rafraîchisse
        _photos.value = filteredList
    }

    fun toggleLike(photoId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val profile = userRepository.getUserProfile(userId)
            val userName = profile?.username ?: auth.currentUser?.email ?: "Anonyme"
            repository.toggleLike(photoId, userId, userName)
        }
    }
}
