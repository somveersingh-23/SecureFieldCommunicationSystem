package com.kaidwal.securefieldcommunicationsystem

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Mesh Network Router
 * Implements message routing and multi-hop forwarding
 */
class MeshRouter {

    private val routingTable = ConcurrentHashMap<String, List<String>>()
    private val messageCache = ConcurrentHashMap<String, Long>()

    private val _routingState = MutableStateFlow<RoutingState>(RoutingState.Idle)
    val routingState: StateFlow<RoutingState> = _routingState

    companion object {
        private const val MAX_HOPS = 5
        private const val MESSAGE_CACHE_TTL = 60_000L // 1 minute
    }

    sealed class RoutingState {
        object Idle : RoutingState()
        data class Routing(val messageId: String, val hopCount: Int) : RoutingState()
        data class Delivered(val messageId: String) : RoutingState()
        data class Failed(val messageId: String, val reason: String) : RoutingState()
    }

    data class MessagePacket(
        val messageId: String,
        val senderId: String,
        val receiverId: String,
        val encryptedPayload: ByteArray,
        val hopCount: Int,
        val timestamp: Long
    )

    /**
     * Route message to destination
     */
    fun routeMessage(
        packet: MessagePacket,
        availableNodes: List<String>
    ): String? {
        // Check if message already processed
        if (messageCache.containsKey(packet.messageId)) {
            val cachedTime = messageCache[packet.messageId]
            if (cachedTime != null &&
                System.currentTimeMillis() - cachedTime < MESSAGE_CACHE_TTL) {
                Timber.d("Message ${packet.messageId} already processed")
                return null
            }
        }

        // Check hop limit
        if (packet.hopCount >= MAX_HOPS) {
            _routingState.value = RoutingState.Failed(
                packet.messageId,
                "Max hops ($MAX_HOPS) exceeded"
            )
            Timber.w("Message ${packet.messageId} exceeded max hops")
            return null
        }

        // Check if destination is directly available
        if (availableNodes.contains(packet.receiverId)) {
            Timber.d("Direct route to ${packet.receiverId}")
            _routingState.value = RoutingState.Delivered(packet.messageId)
            return packet.receiverId
        }

        // Find best next hop
        val nextHop = selectNextHop(packet.receiverId, availableNodes)
        if (nextHop == null) {
            _routingState.value = RoutingState.Failed(
                packet.messageId,
                "No route available"
            )
            Timber.w("No route found for message ${packet.messageId}")
            return null
        }

        // Cache message
        messageCache[packet.messageId] = System.currentTimeMillis()

        _routingState.value = RoutingState.Routing(packet.messageId, packet.hopCount + 1)
        Timber.d("Routing message ${packet.messageId} via $nextHop (hop ${packet.hopCount + 1})")

        return nextHop
    }

    /**
     * Select next hop using greedy algorithm
     */
    private fun selectNextHop(
        destinationId: String,
        availableNodes: List<String>
    ): String? {
        // Check routing table for known path
        val knownPath = routingTable[destinationId]
        if (knownPath != null) {
            val nextHop = knownPath.firstOrNull { it in availableNodes }
            if (nextHop != null) {
                Timber.d("Using known route: $nextHop")
                return nextHop
            }
        }

        // Select first available node
        return availableNodes.firstOrNull()
    }

    /**
     * Update routing table with discovered path
     */
    fun updateRoutingTable(destinationId: String, path: List<String>) {
        routingTable[destinationId] = path
        Timber.d("Updated routing table for $destinationId")
    }

    /**
     * Clean expired messages from cache
     */
    fun cleanMessageCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = messageCache.entries
            .filter { currentTime - it.value > MESSAGE_CACHE_TTL }
            .map { it.key }

        expiredKeys.forEach { messageCache.remove(it) }

        if (expiredKeys.isNotEmpty()) {
            Timber.d("Cleaned ${expiredKeys.size} expired messages from cache")
        }
    }

    /**
     * Get routing statistics
     */
    fun getRoutingStats(): Map<String, Any> {
        return mapOf(
            "knownRoutes" to routingTable.size,
            "cachedMessages" to messageCache.size
        )
    }
}
