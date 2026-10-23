package io.github.sarat03.phonewarden

import android.content.Context
import android.provider.Settings
import io.github.sarat03.phonewarden.tls.AdbKey
import io.github.sarat03.phonewarden.tls.TlsAdbTransport
import java.io.Closeable
import java.net.ConnectException
import java.net.InetAddress
import phonewarden.adb.AdbConnection
import phonewarden.engine.DeviceFacts
import phonewarden.engine.Finding
import phonewarden.engine.Shell
import phonewarden.engine.access
import phonewarden.engine.findings
import phonewarden.engine.network

/** One connection to this phone's adbd, from "Wireless debugging is on" to "switched off and verified". */
class Session private constructor(private val transport: TlsAdbTransport, private val conn: AdbConnection, val port: Int) : Closeable {
    val shell: Shell = conn.asShell()

    fun check(ctx: Context): List<Finding> = findings(
        network(shell),
        access(shell).let { a -> a.copy(outsidePlayStore = a.outsidePlayStore.filterNot { it.first.startsWith(ctx.packageName) }) }, // not ourselves
        DeviceFacts(DeviceChecks.deviceAdmins(ctx), DeviceChecks.userCertificates(), DeviceChecks.vpnActive(ctx)),
        ownAdbPort = port,
    )

    /** Switches Wireless debugging off over this connection, then verifies from the app side. True when verified off. */
    fun finish(ctx: Context): Boolean {
        conn.disableWirelessDebugging()
        close()
        Thread.sleep(1000) // adbd closes its listener asynchronously
        val portClosed = try { java.net.Socket(InetAddress.getLoopbackAddress(), port).close(); false } catch (_: ConnectException) { true }
        return !isWirelessDebuggingOn(ctx) && portClosed
    }

    override fun close() = transport.close()

    companion object {
        fun isWirelessDebuggingOn(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1

        /** Finds the connect port by mDNS and connects with our paired key. */
        fun open(ctx: Context): Session {
            val ports = Mdns.ports(ctx, Mdns.CONNECT)
            if (ports.isEmpty()) throw IllegalStateException("Could not find Wireless debugging. Make sure it is on.")
            var last: Exception? = null
            for (port in ports) {
                var t: TlsAdbTransport? = null
                try {
                    t = TlsAdbTransport(InetAddress.getLoopbackAddress(), port, AdbKey.getOrCreate())
                    return Session(t, AdbConnection(t).apply { connect() }, port)
                } catch (e: Exception) {
                    t?.close(); last = e
                }
            }
            throw IllegalStateException("Could not connect (${last?.message}). If you paired before, pair again.")
        }
    }
}
