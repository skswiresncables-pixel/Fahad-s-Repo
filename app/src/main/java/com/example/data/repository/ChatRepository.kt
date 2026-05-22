package com.example.data.repository

import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class ChatRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val chatGroupDao: ChatGroupDao,
    private val callHistoryDao: CallHistoryDao,
    private val integrationDao: IntegrationDao
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()
    val allGroups: Flow<List<ChatGroup>> = chatGroupDao.getAllGroups()
    val callHistory: Flow<List<CallHistory>> = callHistoryDao.getAllCalls()
    val integrations: Flow<List<UnifiedInboxIntegration>> = integrationDao.getAllIntegrations()

    fun getMessagesForChat(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId)
    }

    fun getContactById(contactId: String): Flow<Contact?> {
        return contactDao.getContactById(contactId)
    }

    fun getGroupById(groupId: String): Flow<ChatGroup?> {
        return chatGroupDao.getGroupById(groupId)
    }

    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }

    suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message)
    }

    suspend fun deleteMessageById(id: Int) {
        messageDao.deleteMessageById(id)
    }

    suspend fun createGroup(group: ChatGroup) {
        chatGroupDao.insertGroup(group)
    }

    suspend fun deleteGroupById(groupId: String) {
        chatGroupDao.deleteGroupById(groupId)
    }

    suspend fun logCall(call: CallHistory) {
        callHistoryDao.insertCall(call)
    }

    suspend fun deleteCall(call: CallHistory) {
        callHistoryDao.deleteCall(call)
    }

    suspend fun updateIntegration(integration: UnifiedInboxIntegration) {
        integrationDao.insertIntegration(integration)
    }

    // Populates initial realistic mock data if contacts is empty
    suspend fun checkAndPrepopulate() {
        val existingContacts = contactDao.getAllContacts().firstOrNull() ?: emptyList()
        if (existingContacts.isNotEmpty()) return

        // 1. Insert Contacts (Email and Cellphone)
        val contact1 = Contact(
            id = "sarah.j@enterprise.com",
            name = "Sarah Jenkins",
            customNotificationSound = "Chime",
            customNotificationVibrate = true,
            customNotificationMute = false,
            isFavorite = true,
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80",
            isEmailRegistered = true
        )
        val contact2 = Contact(
            id = "+1 (555) 048-1192",
            name = "David Chen (Architect)",
            customNotificationSound = "Default",
            customNotificationVibrate = true,
            customNotificationMute = false,
            isFavorite = false,
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80",
            isEmailRegistered = false
        )
        val contact3 = Contact(
            id = "amara@techcorp.io",
            name = "Amara Okeke (Lead Dev)",
            customNotificationSound = "Pulse",
            customNotificationVibrate = false,
            customNotificationMute = true,
            isFavorite = true,
            avatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=150&q=80",
            isEmailRegistered = true
        )
        val contact4 = Contact(
            id = "+1 (555) 987-6543",
            name = "Marcus Vance",
            customNotificationSound = "Silent",
            customNotificationVibrate = true,
            customNotificationMute = false,
            isFavorite = false,
            avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80",
            isEmailRegistered = false
        )

        contactDao.insertContact(contact1)
        contactDao.insertContact(contact2)
        contactDao.insertContact(contact3)
        contactDao.insertContact(contact4)

        // 2. Direct Messages (Encrypted)
        val now = System.currentTimeMillis()
        messageDao.insertMessage(Message(chatId = contact1.id, senderId = contact1.id, senderName = contact1.name, content = "Hi! Did we apply the end-to-end keys to the team architecture specs yet?", timestamp = now - 3600000 * 2, isEncrypted = true))
        messageDao.insertMessage(Message(chatId = contact1.id, senderId = "me", senderName = "Me", content = "Yes, absolute end-to-end encryption is fully active now! Perfect privacy.", timestamp = now - 3600000 * 1, isEncrypted = true))
        messageDao.insertMessage(Message(chatId = contact1.id, senderId = contact1.id, senderName = contact1.name, content = "Awesome, and the media transfer works beautifully.", timestamp = now - 600000, isEncrypted = true, mediaType = "image", mediaUri = "https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&w=300&q=80"))

        messageDao.insertMessage(Message(chatId = contact2.id, senderId = contact2.id, senderName = contact2.name, content = "Hey, let's schedule our voice sync-up call.", timestamp = now - 7200000, isEncrypted = true))
        messageDao.insertMessage(Message(chatId = contact2.id, senderId = "me", senderName = "Me", content = "Sounds good! I'll call you in 5 minutes.", timestamp = now - 6600000, isEncrypted = true))

        messageDao.insertMessage(Message(chatId = contact3.id, senderId = contact3.id, senderName = contact3.name, content = "Deployment successful. Secure protocols resolved with zero latency.", timestamp = now - 3600000 * 5, isEncrypted = true))

        // 3. Social Media Aggregated Messages (Unified Inbox)
        // These are channeled via specific social media sources marked as "x", "facebook", "instagram", "linkedin"
        messageDao.insertMessage(Message(chatId = "unified_inbox", senderId = "x", senderName = "@TechNexus (X)", content = "Nice layout in the new app preview! We retweeted.", timestamp = now - 3600000 * 4, isEncrypted = false))
        messageDao.insertMessage(Message(chatId = "unified_inbox", senderId = "facebook", senderName = "Facebook Message from Leo", content = "Hey partner, check out our design requirements document.", timestamp = now - 3600000 * 3, isEncrypted = false, mediaType = "document", mediaUri = "PDF Project_Guide.pdf"))
        messageDao.insertMessage(Message(chatId = "unified_inbox", senderId = "linkedin", senderName = "Alice Wang (LinkedIn Connect)", content = "Hi, I am interested in joining as a security consultant.", timestamp = now - 5400000, isEncrypted = false))
        messageDao.insertMessage(Message(chatId = "unified_inbox", senderId = "instagram", senderName = "Insta_Brand (Instagram)", content = "Loved your latest post about private real-time networking! 🔥", timestamp = now - 1800000, isEncrypted = false))

        // 4. Groups and Broadcast lists
        val memberIdsGroup = "${contact1.id},${contact2.id},${contact3.id}"
        val group1 = ChatGroup(groupId = "group_1", name = "Product Strategy Core 🔐", memberIds = memberIdsGroup, isBroadcast = false)
        chatGroupDao.insertGroup(group1)

        val memberIdsBroadcast = "${contact1.id},${contact3.id}"
        val broadcast1 = ChatGroup(groupId = "broadcast_1", name = "VIP Investors & Partners 📢", memberIds = memberIdsBroadcast, isBroadcast = true)
        chatGroupDao.insertGroup(broadcast1)

        // Prepopulate Group Chats
        messageDao.insertMessage(Message(chatId = "group_1", senderId = contact3.id, senderName = contact3.name, content = "E2E key hashes generated and verified by the security framework.", timestamp = now - 1800000, isEncrypted = true))
        messageDao.insertMessage(Message(chatId = "group_1", senderId = "me", senderName = "Me", content = "Outstanding. This guarantees absolute confidence.", timestamp = now - 900000, isEncrypted = true))

        // 5. Calling Logs (Voice & Video Calls)
        callHistoryDao.insertCall(CallHistory(contactId = contact1.id, contactName = contact1.name, isGroup = false, isVideo = true, timestamp = now - 86400000, durationSecs = 245, wasMissed = false))
        callHistoryDao.insertCall(CallHistory(contactId = contact2.id, contactName = contact2.name, isGroup = false, isVideo = false, timestamp = now - 3600000 * 18, durationSecs = 0, wasMissed = true))
        callHistoryDao.insertCall(CallHistory(contactId = "group_1", contactName = "Product Strategy Core", isGroup = true, isVideo = true, timestamp = now - 3600000 * 30, durationSecs = 1800, wasMissed = false))

        // 6. Connect Integrations
        integrationDao.insertIntegration(UnifiedInboxIntegration("x", "@fahad_builds", true, now - 500000))
        integrationDao.insertIntegration(UnifiedInboxIntegration("facebook", "Fahad Cooks", true, now - 600000))
        integrationDao.insertIntegration(UnifiedInboxIntegration("instagram", "@fahad_cooks", true, now - 600000))
        integrationDao.insertIntegration(UnifiedInboxIntegration("linkedin", "Fahad Dev", false, now - 1000000))
    }
}
