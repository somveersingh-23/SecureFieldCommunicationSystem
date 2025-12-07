package com.kaidwal.securefieldcommunicationsystem

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

/**
 * Encrypted Room Database using SQLCipher
 */
@Database(
    entities = [DeviceEntity::class, MessageEntity::class, RouteLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DeviceDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun messageDao(): MessageDao
    abstract fun routeLogDao(): RouteLogDao

    companion object {
        @Volatile
        private var INSTANCE: DeviceDatabase? = null

        fun getInstance(context: Context): DeviceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): DeviceDatabase {
            // Generate secure passphrase
            val passphrase = getOrCreatePassphrase(context)

            // Create SQLCipher factory
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

            return Room.databaseBuilder(
                context.applicationContext,
                DeviceDatabase::class.java,
                "sfcs_secure_database.db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        private fun getOrCreatePassphrase(context: Context): String {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                "sfcs_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var passphrase = sharedPreferences.getString("db_passphrase", null)
            if (passphrase == null) {
                // Generate secure random passphrase
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                passphrase = bytes.joinToString("") { "%02x".format(it) }

                sharedPreferences.edit()
                    .putString("db_passphrase", passphrase)
                    .apply()
            }

            return passphrase
        }
    }
}

// ========================= ENTITIES =========================

@Entity(
    tableName = "devices",
    indices = [Index(value = ["deviceId"], unique = true)]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "deviceId")
    val deviceId: String,

    @ColumnInfo(name = "publicKey")
    val publicKey: String,

    @ColumnInfo(name = "displayName")
    val displayName: String? = null,

    @ColumnInfo(name = "lastSeen")
    val lastSeen: Long,

    @ColumnInfo(name = "isConnected")
    val isConnected: Boolean = false,

    @ColumnInfo(name = "connectionType")
    val connectionType: String = "BLUETOOTH" // BLUETOOTH or WIFI_DIRECT
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["senderId"]),
        Index(value = ["receiverId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "messageId")
    val messageId: String,

    @ColumnInfo(name = "senderId")
    val senderId: String,

    @ColumnInfo(name = "receiverId")
    val receiverId: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "status")
    val status: String, // PENDING, SENT, DELIVERED, READ, FAILED

    @ColumnInfo(name = "hopCount")
    val hopCount: Int = 0
)

@Entity(
    tableName = "route_logs",
    indices = [Index(value = ["messageId"])]
)
data class RouteLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "messageId")
    val messageId: String,

    @ColumnInfo(name = "nodeId")
    val nodeId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "action")
    val action: String // RECEIVED, FORWARDED, DELIVERED
)

// ========================= DAOs =========================

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE isConnected = 1")
    suspend fun getConnectedDevices(): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isConnected = :isConnected WHERE deviceId = :deviceId")
    suspend fun updateConnectionStatus(deviceId: String, isConnected: Boolean)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("DELETE FROM devices")
    suspend fun deleteAllDevices()
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    // Show both sent and received messages
    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :deviceId AND receiverId = :currentDeviceId) 
           OR (senderId = :currentDeviceId AND receiverId = :deviceId)
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesForDevice(deviceId: String, currentDeviceId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = :status")
    suspend fun getMessagesByStatus(status: String): List<MessageEntity>

    // Insert without overwriting existing message rows
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    // Delete by messageId (old name you already had)
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    // Get by messageId (alias used in ChatViewModel)
    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageByMessageId(messageId: String): MessageEntity?

    // NEW: Explicit helper used by ChatViewModel cleanup
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Dao
interface RouteLogDao {

    @Query("SELECT * FROM route_logs ORDER BY timestamp DESC")
    suspend fun getAllRouteLogs(): List<RouteLogEntity>

    @Query("SELECT * FROM route_logs WHERE messageId = :messageId ORDER BY timestamp ASC")
    suspend fun getRouteLogsForMessage(messageId: String): List<RouteLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteLog(routeLog: RouteLogEntity)

    @Query("DELETE FROM route_logs WHERE messageId = :messageId")
    suspend fun deleteRouteLogsForMessage(messageId: String)

    @Query("DELETE FROM route_logs")
    suspend fun deleteAllRouteLogs()
}
