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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import java.util.Date
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class KioskHomeTest {
    @get:Rule val rule = createComposeRule()

    private val apps = listOf(App("org.example.maps", "Maps", ImageBitmap(48, 48)), App("org.example.camera", "Camera", ImageBitmap(48, 48)))
    private var opened: String? = null
    private var monoMode = 0

    private fun show() = rule.setContent { KioskHome(apps, Date(0), 80, { opened = it }, { monoMode++ }) }

    @Test fun tappingAnAppOpensIt() {
        show()
        rule.onNodeWithText("Camera").performClick()
        assertEquals("org.example.camera", opened)
    }

    @Test fun eachAppNameIsReadOnce() {
        show()
        rule.onAllNodesWithText("Maps").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Maps").assertCountEquals(0)
    }

    @Test fun holdOpensMonoModeAndTapDoesNot() {
        show()
        val hold = rule.onNodeWithText("Hold for MonoMode")
        hold.performClick()
        assertEquals(0, monoMode)
        hold.performTouchInput { longClick() }
        assertEquals(1, monoMode)
    }

    @Test fun holdIsNamedForTalkBack() {
        show()
        rule.onAllNodesWithText("Hold for MonoMode").assertCountEquals(1)
        rule.onNodeWithText("Hold for MonoMode").assert(SemanticsMatcher("long click labelled") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Open MonoMode" })
    }
}
