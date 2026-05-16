package fr.cestnous.travelwow.travelShare.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.cestnous.travelwow.travelShare.data.model.ChatMessage
import fr.cestnous.travelwow.travelShare.data.repository.ChatRepository
import fr.cestnous.travelwow.travelShare.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val auth = FirebaseAuth.getInstance()
    private var currentChatId: String? = null
    private var isGroupChat: Boolean = false

    fun observeMessages(targetId: String, isGroup: Boolean) {
        currentChatId = targetId
        isGroupChat = isGroup
        val currentUserId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            val flow = if (isGroup) {
                chatRepository.getGroupMessagesFlow(targetId)
            } else {
                chatRepository.getMessagesFlow(currentUserId, targetId)
            }
            
            flow.collectLatest {
                _messages.value = it
            }
        }
    }

    fun sendMessage(targetId: String, text: String, photoId: String? = null, isGroup: Boolean = isGroupChat) {
        if (text.isBlank() && photoId == null) return
        val currentUserId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            val currentUserProfile = userRepository.getUserProfile(currentUserId)
            val senderName = currentUserProfile?.username ?: auth.currentUser?.email ?: "Anonyme"

            if (isGroup) {
                chatRepository.sendGroupMessage(
                    groupId = targetId,
                    senderId = currentUserId,
                    senderName = senderName,
                    text = text,
                    photoId = photoId
                )
            } else {
                chatRepository.sendMessage(currentUserId, senderName, targetId, text, photoId)
            }
        }
    }
}
