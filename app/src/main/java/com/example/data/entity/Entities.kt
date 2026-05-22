package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String, // Phone number or Email address
    val name: String,
    val customNotificationSound: String = "Default",
    val customNotificationVibrate: Boolean = true,
    val customNotificationMute: Boolean = false,
    val isFavorite: Boolean = false,
    val avatarUrl: String? = null,
    val isEmailRegistered: Boolean = false
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatId: String, // Contact ID, or Group/Broadcast ID
    val senderId: String, // "me" or Contact ID
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val mediaType: String? = null, // "image", "audio", "document", or null
    val mediaUri: String? = null,
    val isRead: Boolean = false
)

@Entity(tableName = "chat_groups")
data class ChatGroup(
    @PrimaryKey val groupId: String, // "group_..." or "list_..." for broadcast lists
    val name: String,
    val memberIds: String, // Comma-separated list of contact IDs
    val isBroadcast: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_history")
data class CallHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val contactName: String,
    val isGroup: Boolean = false,
    val isVideo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSecs: Int = 0,
    val wasMissed: Boolean = false
)

@Entity(tableName = "unified_inbox_integrations")
data class UnifiedInboxIntegration(
    @PrimaryKey val platformId: String, // "x", "facebook", "instagram", "linkedin"
    val username: String = "",
    val isConnected: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
