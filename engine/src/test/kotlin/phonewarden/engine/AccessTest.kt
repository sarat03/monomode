package phonewarden.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Fixtures in the format the Android 14 emulator prints. */
class AccessTest {
    private fun sh(a11y: String, listeners: String, installers: String, system: String = "") = Shell { args ->
        when (args) {
            listOf("settings", "get", "secure", "enabled_accessibility_services") -> "$a11y\n"
            listOf("settings", "get", "secure", "enabled_notification_listeners") -> "$listeners\n"
            listOf("pm", "list", "packages", "-3", "-i") -> installers
            listOf("pm", "list", "packages", "-s") -> system
            else -> throw AssertionError("unexpected command $args")
        }
    }

    @Test fun `null settings mean nothing enabled`() {
        val r = access(sh("null", "", ""))
        assertEquals(emptyList(), r.accessibility)
        assertEquals(emptyList(), r.notificationAccess)
    }

    @Test fun `colon separated components become packages`() {
        val r = access(sh(
            "com.evil.spy/.Svc:com.tool.reader/com.tool.reader.Svc",
            "com.google.android.as/com.google.android.apps.miphone.aiai.common.notification.service.AiAiNotificationListenerService:com.google.android.apps.nexuslauncher/com.android.launcher3.notification.NotificationListener",
            "",
        ))
        assertEquals(listOf("com.evil.spy", "com.tool.reader"), r.accessibility)
        assertEquals(listOf("com.google.android.as", "com.google.android.apps.nexuslauncher"), r.notificationAccess)
    }

    @Test fun `apps not installed by the Play Store`() {
        val r = access(sh("null", "null", """
            package:com.whatsapp  installer=com.android.vending
            package:com.sideloaded.app  installer=null
            package:org.fdroid.app  installer=org.fdroid.fdroid
        """.trimIndent() + "\n"))
        assertEquals(listOf("com.sideloaded.app" to null, "org.fdroid.app" to "org.fdroid.fdroid"), r.outsidePlayStore)
    }

    @Test fun `built-in system apps with access are part of Android, not findings`() {
        val r = access(sh(
            "com.tool.reader/.Svc",
            "com.google.android.as/.Listener:com.google.android.apps.nexuslauncher/.Listener:com.chat.app/.Listener",
            "",
            system = "package:com.google.android.as\npackage:com.google.android.apps.nexuslauncher\npackage:android\n",
        ))
        assertEquals(listOf("com.tool.reader"), r.accessibility)
        assertEquals(listOf("com.chat.app"), r.notificationAccess)
        assertEquals(setOf("com.google.android.as", "com.google.android.apps.nexuslauncher", "android"), r.system)
    }
}
