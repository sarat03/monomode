package phonewarden.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Fixtures in the format the Android 14 emulator prints. */
class PinTest {
    private val launcher = """
        19 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.android.camera2/com.android.camera.CameraLauncher
          Activity #1:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.deskclock/com.android.deskclock.DeskClock
    """.trimIndent()
    private val stacks = """
          taskId=25: com.google.android.deskclock/com.android.deskclock.DeskClock bounds=[0,0][1080,2400] userId=0 visible=true topActivity=ComponentInfo{com.google.android.deskclock/com.android.deskclock.DeskClock}
          taskId=12: com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity bounds=[0,0][1080,2400] userId=0 visible=false
    """.trimIndent()

    private val calls = mutableListOf<List<String>>()
    private val sh = Shell { args ->
        calls += args
        when (args.take(3)) {
            listOf("cmd", "package", "query-activities") -> launcher
            listOf("am", "stack", "list") -> stacks
            else -> ""
        }
    }

    @Test fun `launchable apps come from the launcher query`() =
        assertEquals(listOf("com.android.camera2/com.android.camera.CameraLauncher", "com.google.android.deskclock/com.android.deskclock.DeskClock"), launchableApps(sh))

    @Test fun `pin starts the app and locks its task`() {
        pin(sh, "com.google.android.deskclock/com.android.deskclock.DeskClock")
        assertEquals(listOf("am", "start", "-W", "-n", "com.google.android.deskclock/com.android.deskclock.DeskClock"), calls.first())
        assertEquals(listOf("am", "task", "lock", "25"), calls.last())
    }

    @Test fun `pin fails when the app has no task`() {
        assertFailsWith<IllegalStateException> { pin(sh, "com.not.running/.Main") }
    }

    @Test fun `unpin stops the task lock`() {
        unpin(sh)
        assertEquals(listOf(listOf("am", "task", "lock", "stop")), calls)
    }
}
