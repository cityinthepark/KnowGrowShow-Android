package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS `SyncNetworkMonitor`: push / pull skip while offline; regaining network triggers an auto sync. */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(@ApplicationContext context: Context) : NetworkMonitor {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
