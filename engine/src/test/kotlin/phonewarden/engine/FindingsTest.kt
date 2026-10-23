package phonewarden.engine

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindingsTest {
    private fun sock(port: Int, uid: Int, app: String, remote: String = "0.0.0.0:0", state: String = "0A") =
        Socket(state, InetAddress.getByName("0.0.0.0"), port, "0.0.0.0:$port", remote, uid, app)

    private val quiet = NetworkReport(emptyList(), emptyList(), emptyList(), adbExposed = false)
    private val noAccess = AccessReport(emptyList(), emptyList(), emptyList(), emptySet())
    private val nothing = DeviceFacts(emptyList(), emptyList(), vpnActive = false)

    @Test fun `our own wireless debugging listener is not a finding`() {
        val own = sock(41234, 2000, "system(uid 2000)")
        val f = findings(NetworkReport(listOf(own), emptyList(), emptyList(), adbExposed = true), noAccess, nothing, ownAdbPort = 41234)
        assertEquals(emptyList(), f.filter { it.level == Level.LOOK })
    }

    @Test fun `adb on 5555 is a finding even during our session`() {
        val f = findings(NetworkReport(listOf(sock(5555, 2000, "system(uid 2000)")), emptyList(), emptyList(), adbExposed = true), noAccess, nothing, ownAdbPort = 41234)
        assertTrue(f.any { it.level == Level.LOOK && "5555" in it.detail })
    }

    @Test fun `each kind of exposure becomes a finding to look at`() {
        val net = NetworkReport(listOf(sock(8080, 10050, "com.share.app")), listOf(sock(8080, 10050, "com.share.app", "192.168.1.35:54321", "01")), emptyList(), false)
        val access = AccessReport(listOf("com.tool.reader"), listOf("com.chat.app"), listOf("com.sideloaded.app" to null), emptySet())
        val dev = DeviceFacts(listOf("com.mdm.agent"), listOf("CN=Corp Proxy CA"), vpnActive = true)
        val looks = findings(net, access, dev, ownAdbPort = 0).filter { it.level == Level.LOOK }.map { it.title }
        assertEquals(8, looks.size, looks.toString())
    }

    @Test fun `nothing found still says something, without promising anything`() {
        val f = findings(quiet, noAccess, nothing, ownAdbPort = 0)
        assertEquals(1, f.size)
        assertEquals(Level.INFO, f[0].level)
    }

    @Test fun `never says hacked, safe or secure`() {
        val net = NetworkReport(listOf(sock(5555, 2000, "x")), listOf(sock(5555, 2000, "x", "1.2.3.4:5", "01")), listOf(sock(4000, 10077, "com.chat.app", "8.8.8.8:443", "01")), true)
        val all = findings(net, AccessReport(listOf("a.b"), listOf("c.d"), listOf("e.f" to "g.h"), emptySet()), DeviceFacts(listOf("i.j"), listOf("CN=x"), true), 0) +
            findings(quiet, noAccess, nothing, 0)
        for (f in all) for (word in listOf("hack", "safe", "secure", "virus", "malware")) {
            assertTrue(word !in (f.title + " " + f.detail).lowercase(), "'$word' in $f")
        }
    }

    @Test fun `built-in apps are kept but marked system, so the screen can fold them away`() {
        val net = NetworkReport(emptyList(), emptyList(), listOf(
            sock(4000, 10010, "com.google.android.gsf,com.google.android.gms", "1.2.3.4:5228", "01"),
            sock(4001, 1000, "system(uid 1000)", "1.2.3.4:443", "01"),
            sock(4002, 10077, "com.chat.app", "8.8.8.8:443", "01"),
        ), false)
        val access = AccessReport(emptyList(), emptyList(), emptyList(), setOf("com.google.android.gsf", "com.google.android.gms"))
        val f = findings(net, access.copy(notificationAccess = listOf("com.google.android.gms")), nothing, 0)
        assertEquals(listOf("com.chat.app is talking to the internet"), f.filterNot { it.system }.map { it.title })
        assertEquals(setOf("com.google.android.gms can read all your notifications", "com.google.android.gsf,com.google.android.gms is talking to the internet", "system(uid 1000) is talking to the internet"), f.filter { it.system }.map { it.title }.toSet())
    }

    @Test fun `an app group sharing one user id is shortened`() {
        val many = (1..40).joinToString(",") { "com.vendor.part$it" }
        val net = NetworkReport(emptyList(), emptyList(), listOf(sock(4000, 1000, many, "1.2.3.4:443", "01")), false)
        assertEquals("com.vendor.part1 and 39 more apps is talking to the internet", findings(net, noAccess, nothing, 0).first().title)
    }
}
