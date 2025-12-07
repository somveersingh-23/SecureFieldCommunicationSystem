package com.kaidwal.securefieldcommunicationsystem

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Message Manager - FIXED AUTO-DELETE
 * Handles message lifecycle, persistence, and controlled auto-deletion
 */
class MessageManager(
    private val database: DeviceDatabase,
    private val cryptoEngine: CryptoEngine
) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val pendingDeletions = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // CHANGED: Disabled auto-delete for development/testing
        private const val AUTO_DELETE_DELAY = 300000L // 5 minutes (if enabled)
        private const val ENABLE_AUTO_DELETE = false // Set to false to disable auto-delete
    }

    data class Message(
        val messageId: String,
        val senderId: String,
        val receiverId: String,
        val content: String,
        val timestamp: Long,
        val status: MessageStatus,
        val hopCount: Int = 0,
        val isEncrypted: Boolean = true
    )

    enum class MessageStatus {
        PENDING,
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED,
        DELETED
    }

    init {
        // Only start cleanup if auto-delete is enabled
        if (ENABLE_AUTO_DELETE) {
            startAutoDeleteMonitor()
        }
        loadMessagesFromDatabase()
    }

    /**
     * Create new message - FIXED to not replace old messages
     */
    suspend fun createMessage(
        content: String,
        receiverId: String,
        senderId: String
    ): Message {
        val messageId = cryptoEngine.generateMessageId()

        val message = Message(
            messageId = messageId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            hopCount = 0,
            isEncrypted = true
        )

        // Save to database - FIXED: Use INSERT with id=0 to auto-generate
        database.messageDao().insertMessage(
            MessageEntity(
                id = 0, // Auto-generate, don't reuse IDs
                messageId = message.messageId,
                senderId = message.senderId,
                receiverId = message.receiverId,
                content = message.content,
                timestamp = message.timestamp,
                status = message.status.name,
                hopCount = message.hopCount
            )
        )

        // Update UI state
        updateMessageList()

        Timber.d("Created message: $messageId")
        return message
    }

    /**
     * Update message status - FIXED
     */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        database.messageDao().updateMessageStatus(messageId, status.name)

        // CHANGED: Only schedule auto-delete if enabled AND status is READ (not DELIVERED)
        if (ENABLE_AUTO_DELETE && status == MessageStatus.READ) {
            scheduleAutoDelete(messageId)
            Timber.d("Scheduled auto-delete for message: $messageId")
        }

        updateMessageList()
        Timber.d("Updated message $messageId status to $status")
    }

    /**
     * Get message by ID
     */
    suspend fun getMessage(messageId: String): Message? {
        val entity = database.messageDao().getMessageById(messageId)
        return entity?.toMessage()
    }

    /**
     * Get messages for conversation
     */
    suspend fun getMessagesForDevice(deviceId: String, currentDeviceId: String): List<Message> {
        val entities = database.messageDao().getMessagesForDevice(deviceId, currentDeviceId)
        return entities.map { it.toMessage() }
    }

    /**
     * Delete message
     */
    suspend fun deleteMessage(messageId: String) {
        database.messageDao().deleteMessage(messageId)
        updateMessageList()
        Timber.d("Deleted message: $messageId")
    }

    /**
     * Schedule auto-delete after delivery - ONLY IF ENABLED
     */
    private fun scheduleAutoDelete(messageId: String) {
        if (!ENABLE_AUTO_DELETE) {
            Timber.d("Auto-delete disabled, skipping for: $messageId")
            return
        }

        val deleteTime = System.currentTimeMillis() + AUTO_DELETE_DELAY
        pendingDeletions[messageId] = deleteTime

        scope.launch {
            delay(AUTO_DELETE_DELAY)

            // Check if still pending deletion
            if (pendingDeletions.containsKey(messageId)) {
                deleteMessage(messageId)
                pendingDeletions.remove(messageId)
                Timber.d("Auto-deleted message: $messageId")
            }
        }
    }

    /**
     * Cancel auto-delete (e.g., if user reads message)
     */
    fun cancelAutoDelete(messageId: String) {
        pendingDeletions.remove(messageId)
        Timber.d("Cancelled auto-delete for: $messageId")
    }

    /**
     * Start monitoring for auto-deletions
     */
    private fun startAutoDeleteMonitor() {
        scope.launch {
            while (true) {
                delay(10000L) // Check every 10 seconds

                val currentTime = System.currentTimeMillis()
                val toDelete = pendingDeletions.filter { it.value <= currentTime }

                toDelete.forEach { (messageId, _) ->
                    deleteMessage(messageId)
                    pendingDeletions.remove(messageId)
                }
            }
        }
    }

    /**
     * Load messages from database
     */
    private fun loadMessagesFromDatabase() {
        scope.launch {
            updateMessageList()
        }
    }

    /**
     * Update message list in StateFlow
     */
    private suspend fun updateMessageList() {
        val entities = database.messageDao().getAllMessages()
        _messages.value = entities.map { it.toMessage() }
    }

    /**
     * Get message statistics
     */
    suspend fun getMessageStats(): Map<String, Int> {
        val allMessages = database.messageDao().getAllMessages()
        return mapOf(
            "total" to allMessages.size,
            "pending" to allMessages.count { it.status == MessageStatus.PENDING.name },
            "delivered" to allMessages.count { it.status == MessageStatus.DELIVERED.name },
            "failed" to allMessages.count { it.status == MessageStatus.FAILED.name }
        )
    }

    /**
     * Clear all messages
     */
    suspend fun clearAllMessages() {
        database.messageDao().deleteAllMessages()
        _messages.value = emptyList()
        Timber.d("Cleared all messages")
    }
}

// Extension function to convert entity to domain model
private fun MessageEntity.toMessage(): MessageManager.Message {
    return MessageManager.Message(
        messageId = this.messageId,
        senderId = this.senderId,
        receiverId = this.receiverId,
        content = this.content,
        timestamp = this.timestamp,
        status = MessageManager.MessageStatus.valueOf(this.status),
        hopCount = this.hopCount,
        isEncrypted = true
    )
}
