package monomode.engine

import java.net.InetAddress

data class Socket(val state: String, val ip: InetAddress, val port: Int, val local: String, val remote: String, val uid: Int, val app: String)

data class NetworkReport(
    /** Open to the network. */
    val listening: List<Socket>,
    /** Someone connected to one of those. */
    val inbound: List<Socket>,
    /** Connections the phone made. */
    val outbound: List<Socket>,
    val adbExposed: Boolean,
)

private const val LISTEN = "0A"
private const val ESTABLISHED = "01"
private const val SHELL_UID = 2000

/** Kernel hex `addr:port`; addresses are 32-bit words in host (little-endian) order. */
private fun addr(hex: String): Pair<InetAddress, Int> {
    val (ip, port) = hex.split(":")
    val raw = ip.chunked(8).flatMap { word -> word.chunked(2).map { it.toInt(16).toByte() }.reversed() }.toByteArray()
    return InetAddress.getByAddress(raw) to port.toInt(16) // Java turns ::ffff:a.b.c.d into Inet4Address
}

private fun fmt(a: InetAddress, port: Int) = "${a.hostAddress}:$port"

/**
 * TCP sockets from /proc/net (readable by the adb shell user, no root).
 * ponytail: TCP only, UDP when needed.
 */
fun network(sh: Shell): NetworkReport {
    val owners = mutableMapOf<Int, MutableList<String>>()
    for (line in sh.run(listOf("pm", "list", "packages", "-U")).lines()) {
        if (!line.startsWith("package:") || " uid:" !in line) continue
        val (pkg, uid) = line.drop(8).split(" uid:")
        owners.getOrPut(uid.substringBefore(",").trim().toInt()) { mutableListOf() } += pkg
    }
    val socks = listOf("/proc/net/tcp", "/proc/net/tcp6").flatMap { f ->
        sh.run(listOf("cat", f)).lines().drop(1).mapNotNull { line ->
            val col = line.trim().split(Regex("\\s+"))
            if (col.size < 8) return@mapNotNull null
            val (lip, lport) = addr(col[1])
            val (rip, rport) = addr(col[2])
            val uid = col[7].toInt()
            Socket(col[3], lip, lport, fmt(lip, lport), fmt(rip, rport), uid, owners[uid]?.joinToString(",") ?: "system(uid $uid)")
        }
    }
    val listening = socks.filter { it.state == LISTEN && !it.ip.isLoopbackAddress }
    val ports = listening.map { it.port }.toSet()
    val est = socks.filter { it.state == ESTABLISHED && !it.ip.isLoopbackAddress }
    return NetworkReport(
        listening,
        inbound = est.filter { it.port in ports },
        outbound = est.filter { it.port !in ports },
        // ponytail: 5555 or shell-owned listener; Wireless debugging picks random ports, owned by shell (uid 2000)
        adbExposed = listening.any { it.port == 5555 || it.uid == SHELL_UID },
    )
}
