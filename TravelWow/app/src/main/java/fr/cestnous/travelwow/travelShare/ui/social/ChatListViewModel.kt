package fr.cestnous.travelwow.travelShare.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.ChatGroup
import fr.cestnous.travelwow.travelShare.data.model.UserProfile
import fr.cestnous.travelwow.travelShare.data.repository.ChatRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _groups = MutableStateFlow<List<ChatGroup>>(emptyList())
    val groups: StateFlow<List<ChatGroup>> = _groups

    private val _friends = MutableStateFlow<List<UserProfile>>(emptyList())
    val friends: StateFlow<List<UserProfile>> = _friends

    private val auth = FirebaseAuth.getInstance()

    init {
        loadData()
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Load friends for individual chats
            val profile = userRepository.getUserProfile(uid)
            val friendList = mutableListOf<UserProfile>()
            profile?.friends?.forEach { friendId ->
                userRepository.getUserProfile(friendId)?.let { friendList.add(it) }
            }
            _friends.value = friendList

            // Observe groups in real-time
            chatRepository.getUserGroupsFlow(uid).collectLatest {
                _groups.value = it
            }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            chatRepository.createGroup(name, memberIds, uid)
        }
    }
}
