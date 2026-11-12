package com.ratheshr8.beaconsimulator.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class PeerSighting(
    val receiverId: String,
    val label: String,
    val planId: String?,
    val pinX: Float,
    val pinY: Float,
    val beaconKey: String,
    val rssi: Int,
    val distanceMeters: Double,
    val timestampMs: Long,
)

/**
 * Local Wi‑Fi UDP broadcast so multiple Receiver phones share distances
 * to the same paired transmitter for floor-plan multilateration.
 */
class ReceiverPeerSync(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val localReceiverId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    private val running = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<String, PeerSighting>()
    private var listener: ((List<PeerSighting>) -> Unit)? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var broadcastThread: Thread? = null
    private var listenThread: Thread? = null

    @Volatile private var payloadFactory: (() -> PeerSighting?)? = null

    fun setListener(listener: ((List<PeerSighting>) -> Unit)?) {
        this.listener = listener
        emit()
    }

    fun setPayloadFactory(factory: () -> PeerSighting?) {
        payloadFactory = factory
    }

    fun isRunning(): Boolean = running.get()

    fun peerSnapshot(): List<PeerSighting> = peers.values
        .filter { System.currentTimeMillis() - it.timestampMs < PEER_TTL_MS }
        .sortedBy { it.label }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acquireMulticastLock()
        startListen()
        startBroadcast()
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        broadcastThread = null
        listenThread = null
        releaseMulticastLock()
        peers.clear()
        emit()
    }

    private fun startListen() {
        listenThread = thread(name = "beacon-peer-listen", isDaemon = true) {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
            }
            socket = sock
            val buf = ByteArray(2048)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    handlePacket(text)
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
            runCatching { sock.close() }
        }
    }

    private fun startBroadcast() {
        broadcastThread = thread(name = "beacon-peer-broadcast", isDaemon = true) {
            while (running.get()) {
                try {
                    val sighting = payloadFactory?.invoke()
                    if (sighting != null) {
                        val bytes = encode(sighting).toByteArray(StandardCharsets.UTF_8)
                        DatagramSocket().use { sendSock ->
                            sendSock.broadcast = true
                            val packet = DatagramPacket(
                                bytes,
                                bytes.size,
                                InetAddress.getByName("255.255.255.255"),
                                PORT,
                            )
                            sendSock.send(packet)
                        }
                        // Keep our own sighting in the map too (for UI), excluding self from peer list later
                        peers[sighting.receiverId] = sighting
                        pruneAndEmit()
                    }
                } catch (ex: Exception) {
                    Log.w(TAG, "broadcast failed: ${ex.message}")
                }
                try {
                    Thread.sleep(BROADCAST_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun handlePacket(text: String) {
        val sighting = decode(text) ?: return
        if (sighting.receiverId == localReceiverId) return
        peers[sighting.receiverId] = sighting
        pruneAndEmit()
    }

    private fun pruneAndEmit() {
        val now = System.currentTimeMillis()
        peers.entries.removeIf { now - it.value.timestampMs > PEER_TTL_MS }
        emit()
    }

    private fun emit() {
        listener?.invoke(
            peers.values
                .filter { it.receiverId != localReceiverId }
                .filter { System.currentTimeMillis() - it.timestampMs < PEER_TTL_MS }
                .sortedBy { it.label },
        )
    }

    private fun acquireMulticastLock() {
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return
        multicastLock = wifi.createMulticastLock("beacon-simulator-peers").also {
            it.setReferenceCounted(true)
            runCatching { it.acquire() }
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    companion object {
        private const val TAG = "ReceiverPeerSync"
        private const val PREFS = "receiver_peer_sync"
        private const val KEY_ID = "receiver_id"
        const val PORT = 38473
        private const val BROADCAST_INTERVAL_MS = 1_500L
        private const val PEER_TTL_MS = 5_000L
        private const val PREFIX = "BSYNC1|"

        fun encode(s: PeerSighting): String {
            // Use ';' delimiter so beacon keys that contain '|' stay intact.
            return PREFIX + listOf(
                s.receiverId,
                escape(s.label),
                escape(s.planId ?: ""),
                s.pinX.toString(),
                s.pinY.toString(),
                escape(s.beaconKey),
                s.rssi.toString(),
                s.distanceMeters.toString(),
                s.timestampMs.toString(),
            ).joinToString(";")
        }

        fun decode(raw: String): PeerSighting? {
            if (!raw.startsWith(PREFIX)) return null
            val parts = raw.removePrefix(PREFIX).split(';')
            if (parts.size < 9) return null
            return runCatching {
                PeerSighting(
                    receiverId = parts[0],
                    label = unescape(parts[1]),
                    planId = unescape(parts[2]).ifBlank { null },
                    pinX = parts[3].toFloat(),
                    pinY = parts[4].toFloat(),
                    beaconKey = unescape(parts[5]),
                    rssi = parts[6].toInt(),
                    distanceMeters = parts[7].toDouble(),
                    timestampMs = parts[8].toLong(),
                )
            }.getOrNull()
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace(";", "\\;")

        private fun unescape(value: String): String =
            value.replace("\\;", ";").replace("\\\\", "\\")
    }
}
