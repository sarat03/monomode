package io.github.sarat03.monomode

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Finds adbd's Wireless debugging ports through mDNS, as `adb mdns services` does.
 * Every port found is only ever used on loopback, so a service another phone on the
 * network advertises just fails to connect; we never talk to another device.
 */
object Mdns {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    /** Ports advertised for [type] within [timeoutMs]; empty if none. */
    @Suppress("DEPRECATION") // resolveService: the callback API replacing it needs API 34; we support 30+
    fun ports(ctx: Context, type: String, timeoutMs: Long = 8000): List<Int> {
        val nsd = ctx.getSystemService(NsdManager::class.java)
        val found = LinkedBlockingQueue<NsdServiceInfo>()
        val ports = LinkedBlockingQueue<Int>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { found += info }
            override fun onDiscoveryStarted(t: String) = Unit
            override fun onDiscoveryStopped(t: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(t: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(t: String, code: Int) = Unit
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            // resolve one at a time: older NsdManager rejects concurrent resolves
            while (System.currentTimeMillis() < deadline) {
                val info = found.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: break
                val done = LinkedBlockingQueue<Int>()
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) { done += r.port }
                    override fun onResolveFailed(r: NsdServiceInfo, code: Int) { done += -1 }
                })
                done.poll(3, TimeUnit.SECONDS)?.takeIf { it > 0 }?.let { ports += it }
                if (ports.isNotEmpty() && found.isEmpty()) break
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return ports.toList().distinct()
    }
}
