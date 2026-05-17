package fr.cestnous.travelwow.travelShare.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.FriendRequest
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.repository.FriendRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SocialViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val friendRepository: FriendRepository = FriendRepository()
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests

    private val _friends = MutableStateFlow<List<UserProfile>>(emptyList())
    val friends: StateFlow<List<UserProfile>> = _friends

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val auth = FirebaseAuth.getInstance()

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            observeIncomingRequests(uid)
            loadFriends(uid)
            loadDiscoverUsers() // Charger des suggestions au démarrage
        }
    }

    fun loadDiscoverUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentUid = auth.currentUser?.uid
            val allUsers = userRepository.searchUsers("")
            val friendIds = _friends.value.map { it.uid }.toSet()
            
            _searchResults.value = allUsers.filter { 
                it.uid != currentUid && !friendIds.contains(it.uid) 
            }
            _isLoading.value = false
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            loadDiscoverUsers()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val currentUid = auth.currentUser?.uid
            val allResults = userRepository.searchUsers(query)
            val friendIds = _friends.value.map { it.uid }.toSet()
            
            _searchResults.value = allResults.filter { 
                it.uid != currentUid && !friendIds.contains(it.uid) 
            }
            _isLoading.value = false
        }
    }

    private fun observeIncomingRequests(uid: String) {
        viewModelScope.launch {
            friendRepository.getIncomingRequestsFlow(uid).collectLatest {
                _incomingRequests.value = it
            }
        }
    }

    fun loadFriends(uid: String) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfile(uid)
            val friendList = mutableListOf<UserProfile>()
            profile?.friends?.forEach { friendId ->
                userRepository.getUserProfile(friendId)?.let { friendList.add(it) }
            }
            _friends.value = friendList
            
            // Re-filtrer les suggestions après avoir chargé les amis
            loadDiscoverUsers()
        }
    }

    fun sendRequest(toUser: UserProfile) {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val fromUser = userRepository.getUserProfile(currentUid) ?: return@launch
            friendRepository.sendFriendRequest(fromUser, toUser.uid)
        }
    }

    fun acceptRequest(request: FriendRequest) {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val myProfile = userRepository.getUserProfile(currentUid)
            val myUsername = myProfile?.username ?: auth.currentUser?.email ?: "Anonyme"
            
            friendRepository.acceptFriendRequest(request.id, currentUid, request.fromId, myUsername)
            loadFriends(currentUid)
        }
    }
}
