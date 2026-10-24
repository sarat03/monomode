package monomode.engine

import java.io.File
import java.net.SocketException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneTest {
    private val settings = ProfileTest.FakeSettings(mapOf(
        "global zen_mode" to "0", "global low_power" to "0", "global stay_on_while_plugged_in" to "0",
        "home activity" to "com.oppo.launcher/.Launcher",
    ))
    /** settings go to the fake; am answers with one task, so pin finds the app it started. */
    private val sh = Shell { a ->
        if (a.take(3) == listOf("am", "stack", "list")) "  taskId=42: com.example/.Main bounds=[0,0][1080,2340]"
        else if (a[0] == "am") "" else settings.run(a)
    }

    /** Stands in for Session: [checkFails] throws from check, [verifiedOff] is what finish reports. */
    inner class FakeLink(var checkFails: Exception? = null, var verifiedOff: Boolean = true) : Link {
        var finished = false
        override val shell = sh
        override fun check(): List<Finding> { checkFails?.let { throw it }; return listOf(Finding(Level.INFO, "Nothing stood out", "")) }
        override fun finish(): Boolean { finished = true; wireless = !verifiedOff; return verifiedOff }
        override fun close() {}
    }

    private var wireless = true
    private val link = FakeLink()
    private val dir: File = Files.createTempDirectory("phone").toFile()
    private val phone = Phone(
        open = { link }, debloat = Debloat(File(dir, "debloat.txt")), modes = Modes(dir, ModesTest.MapStore()),
        wirelessOn = { wireless }, background = { it() },
    )

    @Test fun `paired with Wireless debugging on connects and checks by itself`() {
        phone.refresh(paired = true)
        assertTrue(phone.state.connected)
        assertEquals("Nothing stood out", phone.state.results?.single()?.title)
        assertNull(phone.state.busy)
    }

    @Test fun `not paired, or Wireless debugging off, stays disconnected without offering Retry`() {
        phone.refresh(paired = false)
        assertFalse(phone.state.connected)
        wireless = false
        phone.refresh(paired = true)
        assertFalse(phone.state.connected)
        assertFalse(phone.state.retryShown)
    }

    @Test fun `pinning switches Wireless debugging off and offers Retry until the next connection`() {
        phone.refresh(paired = true)
        phone.turnOn(Profile.KIOSK)
        phone.pin("com.example/.Main", "Example")
        assertTrue(link.finished)
        assertFalse(phone.state.connected)
        assertTrue(phone.state.retryShown)
        wireless = true
        phone.connect()
        assertTrue(phone.state.connected)
        assertFalse(phone.state.retryShown)
    }

    @Test fun `a dropped connection ends the session and says how to recover`() {
        phone.refresh(paired = true)
        link.checkFails = SocketException("reset")
        phone.check()
        assertFalse(phone.state.connected)
        assertTrue(phone.state.message!!.startsWith("The connection to this phone was lost"))
        assertTrue(phone.state.retryShown)
    }

    @Test fun `a blocked settings write is explained in the phone's own words`() {
        phone.refresh(paired = true)
        link.checkFails = SecurityException("Permission denial: WRITE_SECURE_SETTINGS")
        phone.check()
        assertTrue(phone.state.connected)
        assertTrue("Disable permission monitoring" in phone.state.message!!)
    }

    @Test fun `modes turn on and off through the session and the state follows`() {
        phone.refresh(paired = true)
        phone.turnOn(Profile.E_READER)
        assertEquals(Profile.E_READER, phone.state.activeMode)
        assertEquals("1", settings.values["global zen_mode"])
        phone.turnOff()
        assertNull(phone.state.activeMode)
        assertEquals("0", settings.values["global zen_mode"])
    }

    @Test fun `finish that cannot verify Wireless debugging is off says so`() {
        phone.refresh(paired = true)
        link.verifiedOff = false
        phone.finish()
        assertFalse(phone.state.connected)
        assertNotNull(phone.state.message)
        phone.dismiss()
        assertNull(phone.state.message)
    }

    @Test fun `actions without a session do nothing`() {
        phone.check()
        phone.turnOn(Profile.KIOSK)
        assertNull(phone.state.activeMode)
        assertNull(phone.state.busy)
    }

    @Test fun `the app list stays after removing and restoring, refreshed without a new Show apps`() {
        val pm = EngineTest.FakeShell(listOf("com.a", "com.b"), system = setOf("com.a"))
        val p = Phone(
            open = { object : Link { override val shell = pm; override fun check() = emptyList<Finding>(); override fun finish() = true; override fun close() {} } },
            debloat = Debloat(File(dir, "d2.txt")), modes = Modes(dir, ModesTest.MapStore()), wirelessOn = { true }, background = { it() },
        )
        p.refresh(paired = true)
        p.scan(emptyList(), "self")
        p.remove(listOf("com.a"), Method.REMOVE)
        assertNotNull(p.state.scan)
        assertFalse("com.a" in p.state.scan!!.others)
        p.restore(setOf("com.a"))
        assertTrue("com.a" in p.state.scan!!.others)
    }

    @Test fun `a refused disable gets the plain-words message, not just the command`() {
        val pm = EngineTest.FakeShell(listOf("com.vendor.locked"))
        val p = Phone(
            open = { object : Link { override val shell = pm; override fun check() = emptyList<Finding>(); override fun finish() = true; override fun close() {} } },
            debloat = Debloat(File(dir, "d3.txt")), modes = Modes(dir, ModesTest.MapStore()), wirelessOn = { true }, background = { it() },
        )
        p.refresh(paired = true)
        p.remove(listOf("com.vendor.locked"), Method.DISABLE)
        assertTrue("try Uninstall" in p.state.message.orEmpty(), p.state.message)
        assertTrue(p.state.connected)
    }
}
