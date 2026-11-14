package io.github.sarat03.monomode

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.key
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monomode.engine.Profile
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.ps
import io.github.sarat03.monomode.MainActivity.Companion.tab
import io.github.sarat03.monomode.MainActivity.Companion.openMode

@Composable
internal fun MainActivity.Screen() = NavigationSuiteScaffold(
    navigationSuiteItems = {
        item(tab == Page.HOME || tab == Page.MODES, { openMode = null; tab = Page.HOME }, { Icon(Icons.Outlined.Home, null) }, label = { Text("Home") })
        item(tab == Page.CHECK, { tab = Page.CHECK }, { Icon(AppIcons.Shield, null) }, label = { Text("Network") })
        item(tab == Page.CLEAN, { tab = Page.CLEAN }, { Icon(AppIcons.Broom, null) }, label = { Text("Cleaner") })
        item(tab == Page.SETTINGS, { tab = Page.SETTINGS }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") })
    },
    // phones keep Home's tiles as the navigation; no bottom bar
    layoutType = if (rail()) NavigationSuiteType.NavigationRail else NavigationSuiteType.None,
) {
    Scaffold(contentWindowInsets = WindowInsets(0)) { pad -> Column(Modifier.padding(pad).safeDrawingPadding().padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ps.busy?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, style = MaterialTheme.typography.bodyMedium) } }
        ps.message?.let { Note(it) }
        when {
            tab == Page.SETTINGS -> Paged(getString(R.string.settings)) { SettingsPage() }
            // only when something the user asked for needs Wireless debugging and it was never set up
            ps.needsSetup -> PairScreen()
            else -> SessionScreen()
        }
    } }
}

/** Tablet width with a session: the rail replaces Home's tool rows and the pages' back arrows. */
@Composable
internal fun MainActivity.rail() = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@Composable
internal fun MainActivity.SessionScreen() = when (tab) {
    Page.CHECK -> Paged(getString(R.string.network_access)) { CheckTab() }
    Page.CLEAN -> Paged(getString(R.string.app_cleaner), bottom = cleanupBar()) { CleanupTab() }
    else -> if (twoPane()) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f)) { HomeScroll() }
        Box(Modifier.weight(1f)) { openMode?.let { ModePaged(it) } ?: Text(getString(R.string.choose_a_mode_to_set_it_up), Modifier.padding(top = 72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else openMode?.takeIf { tab == Page.MODES }?.let { ModePaged(it) } ?: HomeScroll()
}

// keyed so switching modes in the side pane doesn't carry one mode's picks into the other
@Composable
internal fun MainActivity.ModePaged(p: Profile) = key(p) { Paged(p.label, back = { openMode = null; tab = Page.HOME }) { ModePage(p) } }

/** Wide tablet with the rail: Home's modes on the left, the open mode's page on the right. */
@Composable
internal fun MainActivity.twoPane() = rail() && currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

@Composable
internal fun MainActivity.HomeScroll() = Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { HomeTab() }

/** A page: back arrow and title on top, content scrolling, an optional action bar pinned at the bottom; Android's Back does [back]. */
@Composable
internal fun MainActivity.Paged(title: String, back: () -> Unit = { tab = Page.HOME }, bottom: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
    Column(Modifier.widthIn(max = 840.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHandler(onBack = back)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // a mode page sits under Home, so it keeps its arrow next to the rail
            if (openMode != null && tab == Page.MODES || !rail()) IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, getString(R.string.back)) }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        bottom?.let { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); it() }
    }
