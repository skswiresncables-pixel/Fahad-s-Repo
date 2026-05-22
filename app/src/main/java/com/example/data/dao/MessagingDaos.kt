package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun getContactById(id: String): Flow<Contact?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Int)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface ChatGroupDao {
    @Query("SELECT * FROM chat_groups ORDER BY createdTimestamp DESC")
    fun getAllGroups(): Flow<List<ChatGroup>>

    @Query("SELECT * FROM chat_groups WHERE groupId = :groupId LIMIT 1")
    fun getGroupById(groupId: String): Flow<ChatGroup?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ChatGroup)

    @Query("DELETE FROM chat_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("DELETE FROM chat_groups")
    suspend fun clear()
}

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistory)

    @Delete
    suspend fun deleteCall(call: CallHistory)

    @Query("DELETE FROM call_history")
    suspend fun clear()
}

@Dao
interface IntegrationDao {
    @Query("SELECT * FROM unified_inbox_integrations")
    fun getAllIntegrations(): Flow<List<UnifiedInboxIntegration>>

    @Query("SELECT * FROM unified_inbox_integrations WHERE platformId = :platformId LIMIT 1")
    fun getIntegration(platformId: String): Flow<UnifiedInboxIntegration?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntegration(integration: UnifiedInboxIntegration)

    @Query("DELETE FROM unified_inbox_integrations")
    suspend fun clear()
}
