package io.github.sarat03.monomode

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import java.util.Date
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class AllAppsTest {
    @get:Rule val rule = createComposeRule()
    private var opened: String? = null
    private var monoMode = 0
    private val pinned = androidx.compose.runtime.mutableStateOf(setOf("org.example.maps"))
    private val showAll = androidx.compose.runtime.mutableStateOf(false)

    private fun show() = rule.setContent {
        AllApps(listOf("org.example.camera" to "Camera", "org.example.maps" to "Maps"), pinned.value, showAll.value, 0, Date(0),
            { opened = it }, { p -> pinned.value = if (p in pinned.value) pinned.value - p else pinned.value + p }, { showAll.value = it }, { monoMode++ }, wirelessOn = true)
    }

    @Test fun homeShowsOnlyPinnedAndOpensOnTap() {
        show()
        rule.onAllNodesWithText("Camera").assertCountEquals(0)
        rule.onNodeWithText("Maps").performClick()
        assertEquals("org.example.maps", opened)
    }

    @Test fun allAppsListsEveryAppAndHoldPins() {
        show()
        rule.onNodeWithText("All apps").performClick()
        rule.onNodeWithText("Camera").performTouchInput { longClick() }
        rule.onNodeWithText("Pin to home").performClick()
        assertEquals(setOf("org.example.maps", "org.example.camera"), pinned.value)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onAllNodesWithText("Camera").assertCountEquals(1)
    }

    @Test fun modesOpensMonoMode() {
        show()
        rule.onNodeWithText("Modes").performClick()
        assertEquals(1, monoMode)
    }

    @Test fun searchFiltersAllApps() {
        show()
        rule.onNodeWithText("All apps").performClick()
        rule.onNodeWithText("Search apps").performTextInput("cam")
        rule.onAllNodesWithText("Maps").assertCountEquals(0)
        rule.onAllNodesWithText("Camera").assertCountEquals(1)
    }

    @Test fun wirelessDebuggingWarningOpensMonoMode() {
        show()
        rule.onNodeWithText("Wireless debugging is on", substring = true).performClick()
        assertEquals(1, monoMode)
    }
}

/** Google's accessibility checks (touch target size, labels, contrast, duplicate descriptions) on each launcher view. */
open class LauncherAccessibilityTest {
    @get:Rule val rule = createComposeRule()
    open val theme = "light"
    private val apps = listOf("io.github.sarat03.monomode" to "MonoMode", "org.example.camera" to "Camera", "org.example.maps" to "Maps")
    private val showAll = androidx.compose.runtime.mutableStateOf(false)

    @org.junit.Before fun setUp() {
        Appearance.mode = theme
        rule.enableAccessibilityChecks()
        rule.setContent { AllApps(apps, setOf("io.github.sarat03.monomode", "org.example.maps"), showAll.value, 0, Date(0), {}, {}, { showAll.value = it }, {}, wirelessOn = true) }
    }

    @Test fun homePassesChecks() { rule.onRoot().tryPerformAccessibilityChecks() }

    @Test fun allAppsPassesChecks() {
        rule.onNodeWithText("All apps").performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun holdMenuPassesChecks() {
        rule.onNodeWithText("All apps").performClick()
        rule.onNodeWithText("Camera").performTouchInput { longClick() }
        rule.onRoot().tryPerformAccessibilityChecks()
    }
}

/** The same checks in dark theme, where the clock and app names once drew black on black. */
class LauncherAccessibilityDarkTest : LauncherAccessibilityTest() {
    override val theme = "dark"

    @Test fun textUsesThemeColour() {
        rule.onNodeWithText("Maps").assertExists()
        // the bug: unset text colour fell back to black; read it back from the rendered text layout
        val color = rule.onNodeWithText("Maps").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.let { get ->
            val out = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); get(out); out.first().layoutInput.style.color
        }
        kotlin.test.assertNotEquals(androidx.compose.ui.graphics.Color.Black, color)
        kotlin.test.assertTrue(color.luminance() > 0.5f, "app name colour $color is too dark for a dark background")
    }
}

/** The real home screen on clean data: nothing pinned, Modes leads to MonoMode, and MonoMode is in All apps. */
class HomeFirstRunTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    @Test fun firstRunPinsNothingAndMonoModeIsInAllApps() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        androidx.test.core.app.ActivityScenario.launch(HomeActivity::class.java).use {
            rule.onAllNodesWithText("MonoMode").assertCountEquals(0)
            rule.onAllNodesWithText("Modes").assertCountEquals(1)
            rule.onNodeWithText("All apps").performClick()
            // search, not scroll: the list only builds rows on screen, and on a small screen (CI's 320x640) MonoMode is below them
            rule.onNodeWithText("Search apps").performTextInput("Mono")
            rule.onAllNodesWithText("MonoMode").assertCountEquals(1)
        }
    }
}

/** Ways back into MonoMode from outside it. */
class WayBackTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createEmptyComposeRule()
    private val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    /** Pairing happens inside Android's Settings; both notifications must lead back to MonoMode. */
    @Test fun pairingNotificationsOpenMonoMode() {
        // granted at setup in the app; a fresh test install has not been asked yet. The permission exists from Android 13 (API 33)
        if (android.os.Build.VERSION.SDK_INT >= 33) inst.uiAutomation.grantRuntimePermission(ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        // notifications are posted asynchronously: wait for the one with [title]
        fun posted(title: String) = (1..50).firstNotNullOfOrNull {
            nm.activeNotifications.firstOrNull { it.id == 1 && it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == title }
                ?.notification ?: run { Thread.sleep(100); null }
        } ?: error("notification \"$title\" never appeared")
        Pairing.prompt(ctx)
        kotlin.test.assertNotNull(posted(ctx.getString(R.string.pair_monomode)).contentIntent, "code notification has no tap action")
        Pairing.done(ctx, "paired")
        kotlin.test.assertNotNull(posted(ctx.getString(R.string.monomode_is_paired)).contentIntent, "paired notification has no tap action")
        nm.cancelAll()
    }

    /** Modes asks MonoMode for its Modes page, which shows the mode that is on (Pin again, Turn off), or Home when none is. */
    @Test fun modesOpensTheModesPage() {
        var sent: android.content.Intent? = null
        // stands in for the real start: records the intent and stops MonoMode from opening
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.component?.className == MainActivity::class.java.name) { sent = intent; android.app.Instrumentation.ActivityResult(0, null) } else null
        }
        inst.addMonitor(monitor)
        try {
            androidx.test.core.app.ActivityScenario.launch(HomeActivity::class.java).use {
                rule.onNodeWithText("Modes").performClick()
                rule.waitUntil(5_000) { sent != null }
                kotlin.test.assertEquals(Page.MODES, sent!!.getIntExtra(Page.EXTRA, -1))
            }
        } finally { inst.removeMonitor(monitor) }
    }
}
