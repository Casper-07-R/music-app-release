package com.koi.backend.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.koi.backend.innertube.AudioQualityTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NetworkCoverageLevel(val displayName: String) {
    EXCELLENT("High Coverage (WiFi / 5G)"),
    GOOD("Good Coverage (4G LTE)"),
    MODERATE("Moderate Coverage (3G / Weak Signal)"),
    LOW("Low Coverage (2G / Fringe Signal)")
}

@OptIn(UnstableApi::class)
class NetworkCoverageMonitor(
    private val context: Context,
    val bandwidthMeter: DefaultBandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
) {
    companion object {
        private const val TAG = "NetworkCoverageMonitor"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _coverageLevel = MutableStateFlow(NetworkCoverageLevel.GOOD)
    val coverageLevel: StateFlow<NetworkCoverageLevel> = _coverageLevel.asStateFlow()

    private val _downstreamBandwidthKbps = MutableStateFlow(0)
    val downstreamBandwidthKbps: StateFlow<Int> = _downstreamBandwidthKbps.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            evaluateNetwork()
        }

        override fun onLost(network: Network) {
            evaluateNetwork()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }

        bandwidthMeter.addEventListener(android.os.Handler(android.os.Looper.getMainLooper())) { _, _, _ ->
            evaluateNetwork()
        }

        evaluateNetwork()
    }

    fun evaluateNetwork() {
        try {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = if (activeNetwork != null) connectivityManager?.getNetworkCapabilities(activeNetwork) else null

            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                _coverageLevel.value = NetworkCoverageLevel.LOW
                return
            }

            val linkKbps = caps.linkDownstreamBandwidthKbps
            _downstreamBandwidthKbps.value = linkKbps

            val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            // Real-time transfer bitrate from ExoPlayer HTTP downloads (bits per second)
            val measuredBitrateBps = bandwidthMeter.bitrateEstimate
            val measuredKbps = if (measuredBitrateBps > 0) (measuredBitrateBps / 1000L).toInt() else -1

            val cellularType = if (hasCellular) getCellularGeneration() else CellularGen.UNKNOWN

            val level = when {
                // If real-time measured throughput is severely bottlenecked (< 75 kbps), prioritize anti-buffering
                measuredKbps in 1..75 -> NetworkCoverageLevel.LOW

                // Wi-Fi / Ethernet with strong bandwidth
                hasWifi || hasEthernet -> {
                    if (linkKbps in 1..300 || (measuredKbps in 1..110)) {
                        NetworkCoverageLevel.MODERATE
                    } else {
                        NetworkCoverageLevel.EXCELLENT
                    }
                }

                // 2G cellular or severe low link bandwidth (< 150 kbps)
                cellularType == CellularGen.GEN_2G || (linkKbps in 1..150) -> NetworkCoverageLevel.LOW

                // 3G cellular or low bandwidth (150 - 500 kbps)
                cellularType == CellularGen.GEN_3G || (linkKbps in 151..500) || (measuredKbps in 1..130) -> NetworkCoverageLevel.MODERATE

                // 5G cellular or high bandwidth (> 3000 kbps)
                cellularType == CellularGen.GEN_5G || linkKbps >= 3000 -> NetworkCoverageLevel.EXCELLENT

                // Standard 4G LTE
                else -> NetworkCoverageLevel.GOOD
            }

            _coverageLevel.value = level
        } catch (e: Exception) {
            Log.w(TAG, "Error evaluating network coverage", e)
        }
    }

    fun getRecommendedQualityTier(): AudioQualityTier {
        return when (_coverageLevel.value) {
            NetworkCoverageLevel.EXCELLENT -> AudioQualityTier.HIGH
            NetworkCoverageLevel.GOOD -> AudioQualityTier.HIGH
            NetworkCoverageLevel.MODERATE -> AudioQualityTier.MEDIUM
            NetworkCoverageLevel.LOW -> AudioQualityTier.LOW
        }
    }

    fun isLowCoverage(): Boolean {
        return _coverageLevel.value == NetworkCoverageLevel.LOW
    }

    private enum class CellularGen {
        GEN_2G, GEN_3G, GEN_4G, GEN_5G, UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun getCellularGeneration(): CellularGen {
        return try {
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                } catch (_: SecurityException) {
                    telephonyManager?.networkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                }
            } else {
                telephonyManager?.networkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            }

            when (networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM -> CellularGen.GEN_2G

                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> CellularGen.GEN_3G

                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_IWLAN -> CellularGen.GEN_4G

                TelephonyManager.NETWORK_TYPE_NR -> CellularGen.GEN_5G

                else -> CellularGen.UNKNOWN
            }
        } catch (e: Exception) {
            CellularGen.UNKNOWN
        }
    }
}
