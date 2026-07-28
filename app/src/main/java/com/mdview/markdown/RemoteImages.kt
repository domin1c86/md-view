package com.mdview.markdown

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Whether a document may reach the network for an image right now. */
enum class RemoteImageAccess { Blocked, Allowed }

/**
 * Defaults to [RemoteImageAccess.Blocked] on purpose: a forgotten provider then fails
 * closed and shows a placeholder, rather than silently ignoring the user's preference
 * and contacting whatever servers an untrusted document points at.
 */
val LocalRemoteImages = staticCompositionLocalOf { RemoteImageAccess.Blocked }

/**
 * Whether [destination] would leave the device.
 *
 * Only remote schemes are gated. A `data:` image is embedded in the document itself and
 * a `content:`/`file:` one is already on the device, so blocking those would cost the
 * user their images for no privacy gain at all.
 */
internal fun isRemoteImage(destination: String?): Boolean {
    val scheme = destination?.substringBefore(':', missingDelimiterValue = "")?.lowercase()
    return scheme == "http" || scheme == "https"
}

/**
 * Emits whether the current network is unmetered.
 *
 * A callback flow rather than a one-shot read, because a one-shot leaves images blocked
 * for the rest of the session after the user joins Wi-Fi, with no way to recover short
 * of reopening the document.
 *
 * "Unmetered" rather than "Wi-Fi": a phone hotspot reports itself as Wi-Fi but still
 * costs money, and a dock's Ethernet is unmetered without being Wi-Fi at all.
 */
fun Context.unmeteredNetwork(): Flow<Boolean> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        trySend(false)
        awaitClose { }
        return@callbackFlow
    }

    fun publish() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        )
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) = publish()
    }

    publish()
    runCatching { manager.registerDefaultNetworkCallback(callback) }
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
}.conflate()
