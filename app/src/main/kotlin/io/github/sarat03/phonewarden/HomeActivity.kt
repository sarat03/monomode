package io.github.sarat03.phonewarden

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
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import phonewarden.engine.Profile
import java.text.DateFormat
import java.util.Date

private const val KOREADER = "org.koreader.launcher"

/** The home screen while a mode with "Own home screen" is on, styled for that mode. */
class HomeActivity : ComponentActivity() {
    private class App(val pkg: String, val label: String, val icon: ImageBitmap)

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var mode by mutableStateOf<Profile?>(null)
    private var now by mutableStateOf(Date())
    /** Set by a Home press; a single kiosk app opens then, but not when the user comes Back from it. */
    private var homePressed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        mode = Profile.entries.firstOrNull { Records.mode(this, it).exists() }
        val picked = mode?.let { homeApps(this, it) }.orEmpty()
        val pm = packageManager
        apps = launcherApps(this).filter { it.first in picked }.map { (pkg, label) -> App(pkg, label, pm.getApplicationIcon(pkg).toBitmap()) }
        if (mode == Profile.KIOSK && apps.size == 1 && homePressed) open(apps[0].pkg)
        homePressed = false
    }

    private fun open(pkg: String) = packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity)
    private fun openPhoneWarden() = startActivity(Intent(this, MainActivity::class.java))

    private fun battery(): Int =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

    /** Kindle/Kobo style: paper white, black serif text, no colour or motion, so it suits the grayscale screen. */
    @Composable
    private fun Reader() {
        val ink = Color.Black
        val serif = FontFamily.Serif
        val main = apps.firstOrNull { it.pkg == KOREADER } ?: apps.firstOrNull()
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = ink, fontFamily = serif, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("${battery()}%", color = ink, fontFamily = serif, fontSize = 16.sp)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ink)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = ink, fontFamily = serif, fontSize = 18.sp)
            Spacer(Modifier.height(40.dp))
            if (main == null) Text("Choose your reading app in PhoneWarden, under E-reader mode.", color = ink, fontFamily = serif, fontSize = 20.sp)
            else Column(
                Modifier.fillMaxWidth().border(2.dp, ink).clickable { open(main.pkg) }.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Continue reading", color = ink, fontFamily = serif, fontSize = 28.sp)
                Text(main.label, color = ink, fontFamily = serif, fontSize = 18.sp)
            }
            Spacer(Modifier.height(32.dp))
            for (a in apps - setOfNotNull(main)) {
                Text(a.label, Modifier.fillMaxWidth().clickable { open(a.pkg) }.padding(vertical = 14.dp), color = ink, fontFamily = serif, fontSize = 20.sp)
                HorizontalDivider(color = Color.LightGray)
            }
            Spacer(Modifier.height(32.dp))
            Text("PhoneWarden", Modifier.clickable { openPhoneWarden() }.padding(vertical = 8.dp), color = Color.Gray, fontFamily = serif, fontSize = 14.sp)
        }
    }

    /** A grid of the chosen apps. The way back to PhoneWarden is a long press on the clock, so a guest does not stumble out. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Kiosk() = MaterialTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(16.dp)) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                Modifier.combinedClickable(onClick = {}, onLongClick = ::openPhoneWarden).padding(8.dp),
                style = MaterialTheme.typography.displayMedium,
            )
            if (apps.isEmpty()) Text("No apps chosen. Hold the clock to open PhoneWarden and choose them under Kiosk mode.", Modifier.padding(8.dp))
            LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    Column(Modifier.clickable { open(a.pkg) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(a.icon, a.label, Modifier.size(56.dp))
                        Text(a.label, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(): ImageBitmap {
    val b = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    setBounds(0, 0, b.width, b.height); draw(Canvas(b))
    return b.asImageBitmap()
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("home_apps", Context.MODE_PRIVATE)
fun homeApps(ctx: Context, p: Profile): Set<String> = prefs(ctx).getStringSet(p.name, emptySet())!!
fun setHomeApps(ctx: Context, p: Profile, pkgs: Set<String>) = prefs(ctx).edit().putStringSet(p.name, pkgs).apply()

/** (package, label) of every app with a launcher icon except PhoneWarden, sorted by label. */
fun launcherApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != ctx.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
}

val Profile.label get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }
