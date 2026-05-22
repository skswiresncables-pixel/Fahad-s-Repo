package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ActiveCallState {
    object Idle : ActiveCallState
    data class Outgoing(val contactName: String, val isVideo: Boolean, val durationSecs: Int = 0) : ActiveCallState
    data class Connected(val contactName: String, val isVideo: Boolean, val durationSecs: Int) : ActiveCallState
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ChatRepository
    private val prefs = application.getSharedPreferences("secure_messenger_prefs", Context.MODE_PRIVATE)

    // Screen State
    var currentScreen by mutableStateOf("home") // "home", "chat_room", "calls", "metrics", "integrations", "settings"
    var selectedChatId by mutableStateOf<String?>(null)
    
    // Call UI State
    var activeCallState by mutableStateOf<ActiveCallState>(ActiveCallState.Idle)

    // Dark theme preference (true = dark, false = light)
    var isDarkMode by mutableStateOf(prefs.getBoolean("dark_mode", true))

    // User Account Profile
    var myNumber by mutableStateOf("+1 (555) 012-3456")
    var myEmail by mutableStateOf("me.private@securenet.io")

    // Contact Adding Helper Form States
    var showAddContactDialog by mutableStateOf(false)
    var showCreateGroupDialog by mutableStateOf(false)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(
            database.contactDao(),
            database.messageDao(),
            database.chatGroupDao(),
            database.callHistoryDao(),
            database.integrationDao()
        )

        viewModelScope.launch {
            repository.checkAndPrepopulate()
        }
    }

    // Reactive StateFlows
    val contacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<ChatGroup>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallHistory>> = repository.callHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val integrations: StateFlow<List<UnifiedInboxIntegration>> = repository.integrations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<Message>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently open chat messages flow
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<Message>> = snapshotFlow { selectedChatId }
        .flatMapLatest { chatId ->
            if (chatId != null) {
                repository.getMessagesForChat(chatId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Metrics calculation (LIVE and reactive!)
    val dashboardMetrics = allMessages.combine(contacts) { msgList, contactList ->
        // Calculate response time (gap between contacts' message and mine)
        var responseGapsSum = 0L
        var responseCounts = 0
        
        // Group messages by chatId to find transitions
        val chatsGrouped = msgList.groupBy { it.chatId }
        for ((_, chatMsgs) in chatsGrouped) {
            val sorted = chatMsgs.sortedBy { it.timestamp }
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val curr = sorted[i]
                if (prev.senderId != "me" && curr.senderId == "me") {
                    val gap = curr.timestamp - prev.timestamp
                    if (gap in 0..86400000) { // Limit to logical response times within 1 day
                        responseGapsSum += gap
                        responseCounts++
                    }
                }
            }
        }

        val averageResponseMinutes = if (responseCounts > 0) {
            (responseGapsSum / responseCounts / 60000.0)
        } else {
            1.2 // Default healthy response delay
        }

        // Engagement level calculation (Active chats ratio)
        val activeChatsCount = chatsGrouped.keys.filter { it != "unified_inbox" }.size
        val totalContactsAndGroups = contactList.size + (groups.value.size)
        val engagementPercentage = if (totalContactsAndGroups > 0) {
            (activeChatsCount.toDouble() / totalContactsAndGroups.toDouble() * 100.0).coerceAtMost(100.0)
        } else {
            75.0
        }

        // Security level
        val secureMessagesCount = msgList.count { it.isEncrypted }
        val encryptionPercentage = if (msgList.isNotEmpty()) {
            (secureMessagesCount.toDouble() / msgList.size.toDouble() * 100.0)
        } else {
            100.0
        }

        Triple(averageResponseMinutes, engagementPercentage, encryptionPercentage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(1.2, 80.0, 100.0))

    // Action creators
    fun toggleTheme() {
        isDarkMode = !isDarkMode
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        isDarkMode = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun selectChat(chatId: String) {
        selectedChatId = chatId
        currentScreen = "chat_room"
    }

    fun goBack() {
        selectedChatId = null
        currentScreen = "home"
    }

    fun sendMessage(content: String, mediaType: String? = null, mediaUri: String? = null) {
        val currentChatId = selectedChatId ?: return
        viewModelScope.launch {
            val isGroupOrList = currentChatId.startsWith("group_") || currentChatId.startsWith("broadcast_")
            val isBroadcast = currentChatId.startsWith("broadcast_")

            if (isBroadcast) {
                // If broadcast list:
                // 1. Log the broadcast log inside the broadcast chat
                val broadcastMsg = Message(
                    chatId = currentChatId,
                    senderId = "me",
                    senderName = "Me",
                    content = "[Broadcast] $content",
                    isEncrypted = true,
                    mediaType = mediaType,
                    mediaUri = mediaUri
                )
                repository.saveMessage(broadcastMsg)

                // 2. Insert separate E2E conversations into each participant's private chat
                val broadcastDetails = groups.value.firstOrNull { it.groupId == currentChatId }
                if (broadcastDetails != null) {
                    val individualIds = broadcastDetails.memberIds.split(",").filter { it.isNotBlank() }
                    individualIds.forEach { contactId ->
                        val singleMsg = Message(
                            chatId = contactId,
                            senderId = "me",
                            senderName = "Me",
                            content = content,
                            isEncrypted = true,
                            mediaType = mediaType,
                            mediaUri = mediaUri
                        )
                        repository.saveMessage(singleMsg)
                    }
                }
            } else {
                // Regular single chat or standard secure group chat
                val msg = Message(
                    chatId = currentChatId,
                    senderId = "me",
                    senderName = "Me",
                    content = content,
                    isEncrypted = !currentChatId.startsWith("unified_inbox"), // Social aggregators are not end-to-end encrypted
                    mediaType = mediaType,
                    mediaUri = mediaUri
                )
                repository.saveMessage(msg)

                // Simulate Auto-Reply response for beautiful real-time feel!
                if (currentChatId != "unified_inbox") {
                    simulateReply(currentChatId)
                }
            }
        }
    }

    private fun simulateReply(chatId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1800) // Beautiful live response time simulation
            if (chatId.startsWith("group_")) {
                val matchingGroup = groups.value.firstOrNull { it.groupId == chatId } ?: return@launch
                val memberList = matchingGroup.memberIds.split(",").filter { it.isNotBlank() }
                if (memberList.isNotEmpty()) {
                    val randomSenderId = memberList.random()
                    val randomSenderName = contacts.value.firstOrNull { it.id == randomSenderId }?.name ?: "Collaborator"
                    val msg = Message(
                        chatId = chatId,
                        senderId = randomSenderId,
                        senderName = randomSenderName,
                        content = "Acknowledged! E2E encrypted protocol is robust on my screen too.",
                        isEncrypted = true
                    )
                    repository.saveMessage(msg)
                }
            } else {
                val contactInfo = contacts.value.firstOrNull { it.id == chatId } ?: return@launch
                val replyContent = when {
                    contactInfo.id.contains("sarah") -> "Perfect! Let me verify the server keys on the remote dashboard."
                    contactInfo.id.contains("amara") -> "Committed. Running synchronization on client state hooks now."
                    else -> "End-to-end secure transmission complete. Everything is responsive."
                }
                val msg = Message(
                    chatId = chatId,
                    senderId = contactInfo.id,
                    senderName = contactInfo.name,
                    content = replyContent,
                    isEncrypted = true
                )
                repository.saveMessage(msg)
            }
        }
    }

    // Call Actions
    fun startCall(contactId: String, isVideo: Boolean) {
        val contactName = contacts.value.firstOrNull { it.id == contactId }?.name ?: "Private Contact"
        viewModelScope.launch {
            activeCallState = ActiveCallState.Outgoing(contactName, isVideo)
            // Save in call logs
            repository.logCall(
                CallHistory(
                    contactId = contactId,
                    contactName = contactName,
                    isGroup = false,
                    isVideo = isVideo,
                    durationSecs = 0,
                    wasMissed = false
                )
            )

            // Simulate call connecting in real time after 2 seconds
            kotlinx.coroutines.delay(2000)
            activeCallState = ActiveCallState.Connected(contactName, isVideo, 0)

            // Simulate counting seconds
            viewModelScope.launch {
                while (activeCallState is ActiveCallState.Connected) {
                    kotlinx.coroutines.delay(1000)
                    val current = activeCallState
                    if (current is ActiveCallState.Connected) {
                        activeCallState = current.copy(durationSecs = current.durationSecs + 1)
                    }
                }
            }
        }
    }

    fun endCall() {
        val currentState = activeCallState
        viewModelScope.launch {
            if (currentState is ActiveCallState.Connected) {
                // Update previous call history log with actual simulated duration!
                val list = callLogs.value
                if (list.isNotEmpty()) {
                    val lastRecord = list.first()
                    repository.deleteCall(lastRecord)
                    repository.logCall(
                        CallHistory(
                            contactId = lastRecord.contactId,
                            contactName = lastRecord.contactName,
                            isGroup = lastRecord.isGroup,
                            isVideo = lastRecord.isVideo,
                            durationSecs = currentState.durationSecs,
                            wasMissed = false,
                            timestamp = lastRecord.timestamp
                        )
                    )
                }
            }
            activeCallState = ActiveCallState.Idle
        }
    }

    // New Contacts Addition
    fun addNewContact(id: String, name: String, isEmailRegistered: Boolean) {
        viewModelScope.launch {
            val contact = Contact(
                id = id,
                name = name,
                isEmailRegistered = isEmailRegistered,
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
            )
            repository.insertContact(contact)
        }
    }

    // Custom Notification Settings Configuration
    fun configureContactNotifications(contactId: String, sound: String, vibrate: Boolean, mute: Boolean) {
        viewModelScope.launch {
            val contact = contacts.value.firstOrNull { it.id == contactId } ?: return@launch
            val updated = contact.copy(
                customNotificationSound = sound,
                customNotificationVibrate = vibrate,
                customNotificationMute = mute
            )
            repository.updateContact(updated)
        }
    }

    // Dynamic Group Chats & Broadcast Lists Creator
    fun createGroupOrBroadcast(name: String, memberIds: List<String>, isBroadcast: Boolean) {
        viewModelScope.launch {
            val randomId = if (isBroadcast) "broadcast_${UUID.randomUUID()}" else "group_${UUID.randomUUID()}"
            val group = ChatGroup(
                groupId = randomId,
                name = name,
                memberIds = memberIds.joinToString(","),
                isBroadcast = isBroadcast
            )
            repository.createGroup(group)

            // Log initial helper message
            val infoText = if (isBroadcast) {
                "You created a broadcast list. Messages sent to this list will go to recipients individually and privately."
            } else {
                "End-to-end encrypted secure group initiated."
            }
            repository.saveMessage(
                Message(
                    chatId = randomId,
                    senderId = "system",
                    senderName = "System",
                    content = infoText,
                    isEncrypted = true
                )
            )
        }
    }

    // Account Integrations switcher
    fun toggleIntegrationState(platformId: String, username: String, connect: Boolean) {
        viewModelScope.launch {
            val intObj = UnifiedInboxIntegration(
                platformId = platformId,
                username = if (connect) username else "",
                isConnected = connect,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            repository.updateIntegration(intObj)

            // If connecting, insert message to unified aggregate for positive visual feel
            if (connect) {
                repository.saveMessage(
                    Message(
                        chatId = "unified_inbox",
                        senderId = platformId,
                        senderName = "${platformId.uppercase()} Update",
                        content = "Connected successfully to $username. Aggregated feed is now streaming privately.",
                        isEncrypted = false
                    )
                )
            }
        }
    }
}
