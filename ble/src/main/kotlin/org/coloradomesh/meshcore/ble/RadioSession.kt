package org.coloradomesh.meshcore.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import org.coloradomesh.meshcore.protocol.CompanionClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One radio, from connect to disconnect: the Bluetooth link plus the protocol client that talks
 * over it. A session is used once; make a new one to reconnect.
 *
 * What the radio reports goes to [listener]. The link's own steps go to [onLog]. [onLinkReady]
 * runs once frames can flow, with the largest frame the link carries, just before the client
 * starts reading the radio. [onClosed] runs once when the link ends, with the reason.
 *
 * [onLog], [onLinkReady] and [onClosed] run on the link's own thread; the listener's calls run on
 * the link's thread or the client's ticker, with the client locked. None of them may block.
 */
class RadioSession(
    private val context: Context,
    private val address: String,
    listener: CompanionClient.Listener,
    /** The name the app gives the radio when it says hello; the radio keeps up to 12 bytes. */
    appName: String,
    onLog: (message: String) -> Unit = {},
    onLinkReady: (packetSize: Int) -> Unit = {},
    /** Connection tries before giving up, the first included. */
    connectTries: Int = GattLink.DEFAULT_CONNECT_TRIES,
    /** What the protocol client reads at start-up; see [CompanionClient.StartUp]. */
    startUp: CompanionClient.StartUp = CompanionClient.StartUp(),
    onClosed: (reason: String) -> Unit,
) : GattLink.Events {
    // Kept under their own names so they cannot be mistaken for the GattLink.Events methods.
    private val logTo = onLog
    private val linkReadyTo = onLinkReady
    private val closedTo = onClosed
    private val link = GattLink(context, this, connectTries)
    private val client = CompanionClient(transmit = { link.send(it) }, listener = listener, appName = appName, startUp = startUp)
    private val ticker = Executors.newSingleThreadScheduledExecutor()

    fun open() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            ticker.shutdownNow()
            closedTo("Bluetooth is off")
            return
        }
        link.connect(adapter.getRemoteDevice(address))
    }

    fun close() = link.close()

    fun sendChannelText(channelIndex: Int, text: String) = client.sendChannelText(channelIndex, text)

    /** Returns the id the listener's direct-message result will carry, or null when the radio cannot take it now. */
    fun sendDirectText(publicKeyHex: String, text: String): Long? = client.sendDirectText(publicKeyHex, text)

    /** Ask these companions, by public key, for their position about every [intervalMs]; empty stops. */
    fun setTracking(publicKeys: Set<String>, intervalMs: Long) = client.setTracking(publicKeys, intervalMs)

    override fun onLog(message: String) = logTo(message)

    override fun onReady(packetSize: Int) {
        if (packetSize < COMFORTABLE_PACKET_BYTES) {
            onLog("Warning: packets are limited to $packetSize bytes, so long frames will not fit")
        }
        linkReadyTo(packetSize)
        client.start()
        ticker.scheduleWithFixedDelay({ client.tick() }, 1, 1, TimeUnit.SECONDS)
    }

    override fun onFrame(frame: ByteArray) = client.onFrame(frame)

    override fun onClosed(reason: String) {
        ticker.shutdownNow()
        closedTo(reason)
    }

    private companion object {
        /** Enough for the longest frame the companion protocol sends. */
        const val COMFORTABLE_PACKET_BYTES = 172
    }
}
