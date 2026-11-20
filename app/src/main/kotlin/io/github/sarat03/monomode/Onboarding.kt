package io.github.sarat03.monomode

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGES = 4

/** Shown once after install: what MonoMode is, how a mode works, and the only question setup asks: modes only, or MonoMode as the launcher and which apps its home page holds. */
internal fun MainActivity.onboarded() = launcherPrefs().getBoolean("onboarded", false) || launcherPrefs().getStringSet("pinned", emptySet()).orEmpty().isNotEmpty()
private fun MainActivity.launcherPrefs() = getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)

@Composable
internal fun MainActivity.Onboarding(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val home = remember { mutableStateListOf<String>() }
    var launcher by remember { mutableStateOf(false) }
    fun finish() {
        // the launcher reads "pinned" on resume, so these are its home page the next time it opens
        launcherPrefs().edit().putStringSet("pinned", if (launcher) home.toSet() else emptySet()).putBoolean("onboarded", true).apply()
        // Android decides the default home app, so hand over to its own picker; modes only never touches it
        if (launcher) runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        onDone()
    }
    val last = pager.currentPage == PAGES - 1
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton({ scope.launch { pager.animateScrollToPage(PAGES - 1) } }) { Text(getString(R.string.skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = true) { page ->
            // only the visible page animates; the others restart when swiped back to
            val here = pager.currentPage == page
            // pages 2 and 3 are taller than a small phone: they scroll instead of clipping
            val tall = if (page in 1..2) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(Modifier.fillMaxSize().then(tall), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (page) {
                    0 -> { QuietDemo(here); Blurb(R.string.onb_what_title, R.string.onb_what_body) }
                    1 -> ModeDemo()
                    2 -> { StepsDemo(here); Blurb(R.string.onb_how_title, R.string.onb_how_body); DebugNote() }
                    else -> HomeChoice(this, launcher, { launcher = it }, home)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES) {
                val on by animateFloatAsState(if (it == pager.currentPage) 1f else 0f, label = "dot")
                Box(Modifier.padding(4.dp).size((8 + 8 * on).dp, 8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * on)))
            }
        }
        Button({ if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(getString(if (last) R.string.onb_done else R.string.next))
        }
    }
}

@Composable
private fun MainActivity.Blurb(title: Int, body: Int) {
    Text(getString(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Body(body)
}

@Composable
private fun MainActivity.Body(body: Int) {
    Text(getString(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** A grid of apps that, on a tap (or by itself), falls away to the few that matter. */
@Composable
private fun QuietDemo(running: Boolean) {
    var quiet by remember { mutableStateOf(false) }
    LaunchedEffect(running) { quiet = false; while (running) { delay(1800); quiet = !quiet } }
    val colors = listOf(Color(0xFF00695F), Color(0xFF5C6BC0), Color(0xFFEF6C00), Color(0xFFAD1457))
    Column(Modifier.width(220.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { quiet = !quiet }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { c ->
                    val keep = (r + c) % 5 == 0
                    val a by animateFloatAsState(if (quiet && !keep) 0.08f else 1f, spring(), label = "a")
                    val s by animateFloatAsState(if (quiet && keep) 1.15f else if (quiet) 0.7f else 1f, spring(dampingRatio = 0.5f), label = "s")
                    Box(Modifier.weight(1f).aspectRatio(1f).scale(s).alpha(a).clip(RoundedCornerShape(14.dp)).background(colors[(r * 3 + c) % colors.size]))
                }
            }
        }
    }
}

/** Three chips; the phone and the text under it change to match the one picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainActivity.ModeDemo() {
    var pick by remember { mutableStateOf(0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        listOf(R.string.onb_e_reader, R.string.onb_kiosk, R.string.onb_launcher).forEachIndexed { i, s ->
            FilterChip(pick == i, { pick = i }, { Text(getString(s)) })
        }
    }
    val bg by animateColorAsState(if (pick == 1) Color(0xFF101010) else Color(0xFFEDEDED), label = "bg")
    Box(Modifier.size(160.dp, 220.dp).clip(RoundedCornerShape(28.dp)).background(bg).padding(16.dp), contentAlignment = Alignment.Center) {
        Crossfade(pick, label = "mode") { k ->
            when (k) {
                1 -> Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF00695F)))
                2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { r -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) { c -> Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF8A9A98).copy(alpha = if ((r + c) % 2 == 0) 1f else 0.6f))) }
                    } }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(6) { Box(Modifier.size(if (it == 5) 60.dp else 120.dp, 6.dp).clip(CircleShape).background(Color(0xFF777777))) } }
            }
        }
    }
    Text(getString(R.string.onb_modes_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Crossfade(pick, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, label = "body") {
        Body(listOf(R.string.onb_body_e_reader, R.string.onb_body_kiosk, R.string.onb_body_launcher)[it])
    }
}

/** Why Wireless debugging is in the picture, in one small card. */
@Composable
private fun MainActivity.DebugNote() {
    Text(getString(R.string.onb_debug_body), Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Install, turn on, turn off: each lights up in turn, or when tapped. */
@Composable
private fun MainActivity.StepsDemo(running: Boolean) {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(running, step) { if (running) { delay(1600); step = (step + 1) % 3 } }
    listOf(R.string.onb_step_install, R.string.onb_step_on, R.string.onb_step_off).forEachIndexed { i, s ->
        val lit by animateFloatAsState(if (i == step) 1f else 0f, label = "lit")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { step = i }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.17f * lit)).padding(16.dp).scale(1f + 0.03f * lit), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp)); Text(getString(s), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The one question setup asks: modes only (default) or MonoMode as the launcher; only the launcher needs the apps question. */
@Composable
private fun MainActivity.HomeChoice(scope: androidx.compose.foundation.layout.ColumnScope, launcher: Boolean, choose: (Boolean) -> Unit, home: MutableList<String>) = with(scope) {
    Text(getString(R.string.onb_choice_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Column(Modifier.selectableGroup()) {
        for ((on, title, sub) in listOf(Triple(false, R.string.onb_modes_only, R.string.onb_modes_only_sub), Triple(true, R.string.onb_use_launcher, R.string.onb_use_launcher_sub))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(launcher == on, role = Role.RadioButton) { choose(on) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(launcher == on, null); Spacer(Modifier.width(12.dp))
                Column { Text(getString(title), style = MaterialTheme.typography.bodyLarge); Text(getString(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    Text(getString(R.string.onb_modes_need), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    if (launcher) HomeAppsPicker(scope, home)
}

@Composable
private fun MainActivity.HomeAppsPicker(scope: androidx.compose.foundation.layout.ColumnScope, home: MutableList<String>) = with(scope) {
    Text(getString(R.string.onb_home_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(getString(R.string.onb_home_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    val all = remember { launcherApps(this@HomeAppsPicker).filter { it.first != packageName } }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(all, key = { it.first }) { (pkg, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(pkg in home, role = Role.Checkbox) { if (it) home += pkg else home -= pkg },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(pkg in home, null); Spacer(Modifier.width(12.dp)); AppIcon(pkg); Text(label, Modifier.padding(start = 12.dp))
            }
        }
    }
}
