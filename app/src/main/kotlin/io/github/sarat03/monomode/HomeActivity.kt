package io.github.sarat03.monomode

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import monomode.engine.Profile
import java.text.DateFormat
import java.util.Date

/** The home screen while a mode with "Own home screen" is on, styled for that mode. */
class HomeActivity : ComponentActivity() {
    private class App(val pkg: String, val label: String, val icon: ImageBitmap)

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var mode by mutableStateOf<Profile?>(null)
    private var reader by mutableStateOf<App?>(null)
    /** Drawn once per app; the screen reloads on every return from an app. shortcut: an updated app keeps its old icon until the home screen restarts. */
    private val icons = HashMap<String, ImageBitmap>()
    private var now by mutableStateOf(Date())
    /** Set by a Home press; a single kiosk app opens then, but not when the user comes Back from it. */
    private var homePressed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.load(this)
        // a home screen ignores Back; finishing would reveal the phone's usual launcher underneath
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            LaunchedEffect(Unit) { while (true) { now = Date(); delay(30_000) } }
            if (mode == Profile.E_READER) Reader() else Kiosk()
        }
        // full screen, after setContent (the window has no decor before it on Android 11): the home screens draw their own clock; a swipe from the top shows the bars briefly
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed = true
    }

    override fun onResume() {
        super.onResume()
        val modes = Records.modes(this)
        mode = modes.active
        val picked = mode?.let { modes.apps(it) }.orEmpty()
        val pm = packageManager
        val all = launcherApps(this)
        fun app(p: Pair<String, String>) = App(p.first, p.second, icons.getOrPut(p.first) { pm.getApplicationIcon(p.first).toBitmap((72 * resources.displayMetrics.density).toInt()) })
        val readerPkg = modes.reader(all.map { it.first })
        reader = all.firstOrNull { it.first == readerPkg }?.let(::app)
        apps = all.filter { it.first in picked && (mode != Profile.E_READER || it.first != readerPkg) }.map(::app)
        if (mode == Profile.KIOSK && apps.size == 1 && homePressed) open(apps[0].pkg)
        homePressed = false
    }

    // each app in its own task, like any launcher: Home clears this screen's task, and anything started
    // inside it would be closed too (MonoMode's screen closing ends the session and turns off Wireless debugging)
    private fun open(pkg: String) = packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    // lands on the Modes page, where the mode is turned off; CLEAR_TOP|SINGLE_TOP reaches a running copy through onNewIntent
    private fun openMonoMode() = startActivity(
        Intent(this, MainActivity::class.java).putExtra(Page.EXTRA, Page.MODES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )

    private fun battery(): Int =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

    /** Kindle/Kobo style: paper white, black serif text, no colour or motion, so it suits the grayscale screen. */
    @Composable
    private fun Reader() {
        val dark = isDark()
        val ink = if (dark) Color.White else Color.Black
        val paper = if (dark) Color.Black else Color.White
        val serif = FontFamily.Serif
        val main = reader
        Column(Modifier.fillMaxSize().background(paper).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = ink, fontFamily = serif, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("${battery()}%", color = ink, fontFamily = serif, fontSize = 16.sp)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ink)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = ink, fontFamily = serif, fontSize = 18.sp)
            Spacer(Modifier.height(40.dp))
            if (main == null) Text("No reading app found. Install one (ReadEra, KOReader…) or choose it in MonoMode, under E-reader mode.", color = ink, fontFamily = serif, fontSize = 20.sp)
            else Row(
                Modifier.fillMaxWidth().border(2.dp, ink).clickable { open(main.pkg) }.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                // a book outline, like the cover slot on a Kobo or Kindle home screen
                Box(Modifier.size(56.dp, 80.dp).border(1.5.dp, ink))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Continue reading", color = ink, fontFamily = serif, fontSize = 22.sp, maxLines = 1)
                    Text(main.label, color = ink, fontFamily = serif, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            for (a in apps) {
                Text(a.label, Modifier.fillMaxWidth().clickable { open(a.pkg) }.padding(vertical = 14.dp), color = ink, fontFamily = serif, fontSize = 20.sp)
                HorizontalDivider(color = Color.Gray)
            }
            Spacer(Modifier.height(32.dp))
            Text("MonoMode", Modifier.clickable { openMonoMode() }.padding(vertical = 8.dp), color = Color.Gray, fontFamily = serif, fontSize = 14.sp)
        }
    }

    /** A grid of the chosen apps. The way back to MonoMode is a long press on the clock, so a guest does not stumble out. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Kiosk() = AppTheme {
        // fewer apps, bigger icons, within normal launcher sizes: app icons are drawn for about 48dp and blur when stretched far past it
        val icon = (96f / kotlin.math.sqrt(apps.size.coerceAtLeast(1).toFloat())).coerceIn(48f, 72f).dp
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                    Modifier.combinedClickable(onClick = {}, onLongClick = ::openMonoMode).padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.weight(1f))
                Text("${battery()}%", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            }
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            if (apps.isEmpty()) Text("No apps chosen. Open MonoMode from the bottom of the screen and choose them under Kiosk mode.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface)
            LazyVerticalGrid(GridCells.Adaptive(icon + 32.dp), Modifier.weight(1f).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    Column(Modifier.clickable { open(a.pkg) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // plain tile behind the app's own icon (design A): calm, and hides the white squares of older icons
                        Box(Modifier.size(icon + 16.dp).clip(RoundedCornerShape(24)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Image(a.icon, a.label, Modifier.size(icon).clip(RoundedCornerShape(22)))
                        }
                        Text(a.label, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // visible but needs a hold, so a guest does not leave kiosk by accident
            Text(
                "Hold for MonoMode",
                Modifier.align(Alignment.CenterHorizontally).combinedClickable(onClick = {}, onLongClick = ::openMonoMode).padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}


/** Settings > Appearance; "system" follows the phone's light or dark setting. */
object Appearance {
    val options = listOf("system" to "Follow the phone", "light" to "Light", "dark" to "Dark")
    var mode by mutableStateOf("system")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load(ctx: Context) { mode = prefs(ctx).getString("mode", "system")!! }
    fun set(ctx: Context, m: String) { mode = m; prefs(ctx).edit().putString("mode", m).apply() }
}

@Composable
fun isDark() = when (Appearance.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

/** Material theme in the chosen appearance: a teal scheme, cards on a tinted ground, rounder shapes (redesign). */
@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isDark()) darkColorScheme(
        primary = Color(0xFF82D5C8), onPrimary = Color(0xFF003731), primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFA0F2E4),
        secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E3),
        background = Color(0xFF0E1513), surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1),
        surfaceVariant = Color(0xFF3F4946), onSurfaceVariant = Color(0xFFBEC9C5), outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4946),
        surfaceContainerLowest = Color(0xFF1A211F), surfaceContainer = Color(0xFF1A211F),
    ) else lightColorScheme(
        primary = Color(0xFF00695F), onPrimary = Color.White, primaryContainer = Color(0xFFCCE8E3), onPrimaryContainer = Color(0xFF00201C),
        secondaryContainer = Color(0xFFCCE8E3), onSecondaryContainer = Color(0xFF00201C),
        background = Color(0xFFF5FBF8), surface = Color(0xFFF5FBF8), onSurface = Color(0xFF171D1B),
        surfaceVariant = Color(0xFFE3EAE7), onSurfaceVariant = Color(0xFF3F4946), outline = Color(0xFF6F7976), outlineVariant = Color(0xFFDDE4E1),
        surfaceContainerLowest = Color.White, surfaceContainer = Color(0xFFE9EFEC),
    ),
    shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(28.dp)),
    content = content,
)

/** A plain card on the tinted ground, used in place of outlined cards across the app. */
@Composable
fun Panel(modifier: Modifier = Modifier, border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) = Card(
    modifier, shape = MaterialTheme.shapes.medium, border = border,
    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.onSurface), content = content,
)

/** Draws at [px], the largest size shown, so a single big kiosk icon stays sharp instead of stretching the small built-in bitmap. */
internal fun android.graphics.drawable.Drawable.toBitmap(px: Int): ImageBitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, px, px); draw(Canvas(b))
    return b.asImageBitmap()
}

/** (package, label) of every app with a launcher icon except MonoMode, sorted by label. */
fun launcherApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != ctx.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
}

/** Status colours from the first design: green for safe, amber for things to look at, red for errors. */
class Tone(val bg: Color, val fg: Color)
@Composable fun safeTone() = if (isDark()) Tone(Color(0xFF00504A), Color(0xFFA0F2E4)) else Tone(Color(0xFF00695F), Color.White)
@Composable fun warnTone() = if (isDark()) Tone(Color(0xFF6E3900), Color(0xFFFFDCC2)) else Tone(Color(0xFFFFDCC2), Color(0xFF5C3000))
@Composable fun errorTone() = if (isDark()) Tone(Color(0xFF791F1F), Color(0xFFF7C1C1)) else Tone(Color(0xFFFCEBEB), Color(0xFF791F1F))
