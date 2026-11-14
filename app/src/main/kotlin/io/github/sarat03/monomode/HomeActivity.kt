package io.github.sarat03.monomode

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.system.Os
import android.os.StrictMode
import android.webkit.MimeTypeMap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import monomode.engine.BOOK_TYPES
import monomode.engine.Book
import monomode.engine.BUILT_IN_READER
import monomode.engine.KOREADER
import monomode.engine.koreaderBook
import java.io.File
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import monomode.engine.Profile
import java.text.DateFormat
import java.util.Date

/** The home screen while a mode with "Own home screen" is on, styled for that mode. */
class HomeActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var mode by mutableStateOf<Profile?>(null)
    /** The reading app chosen instead of MonoMode's own reader; null while its own reader is used. */
    private var reader by mutableStateOf<App?>(null)
    /** KOReader's last book and its cover, reread on every return to this screen; null for other reading apps or without All files access. */
    private var book by mutableStateOf<Book?>(null)
    private var cover by mutableStateOf<ImageBitmap?>(null)
    private var resumes by mutableStateOf(0)
    /** The book open in MonoMode's reader, shown in place of the library; null for the library. */
    private var reading by mutableStateOf<String?>(null)
    /** The reader's Settings screen is showing, in place of the library. */
    private var settings by mutableStateOf(false)
    /** Drawn once per app; the screen reloads on every return from an app. shortcut: an updated app keeps its old icon until the home screen restarts. */
    private val icons = HashMap<String, ImageBitmap>()
    private var now by mutableStateOf(Date())
    private var batteryLevel by mutableStateOf(-1)
    /** Set by a Home press; a single kiosk app opens then, but not when the user comes Back from it. */
    private var homePressed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.load(this)
        reading = bookFrom(intent)
        // a home screen ignores Back; finishing would reveal the phone's usual launcher underneath
        onBackPressedDispatcher.addCallback(this) {}
        // KOReader opens a book from a file:// link and reads the file with its own storage access, so nothing is
        // shared through the link; Android's default policy would crash this app for sending one
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        setContent {
            LaunchedEffect(Unit) { while (true) { now = Date(); batteryLevel = battery(); delay(30_000) } }
            LaunchedEffect(resumes, reader?.pkg) {
                val found = if (reader?.pkg == KOREADER && Environment.isExternalStorageManager()) withContext(Dispatchers.IO) { koreaderBook(Environment.getExternalStorageDirectory()) } else null
                if (found?.path != book?.path) cover = found?.let { withContext(Dispatchers.IO) { loadCover(it.path) } }
                book = found
            }
            // a book handed over by another app ("Open with") is read here whatever mode is on
            if (reading != null || mode == Profile.E_READER) { if (reading != null || reader == null) ReaderHome() else Reader() } else KioskHome(apps, now, batteryLevel, { open(it) }, ::openMonoMode)
        }
        // full screen, after setContent (the window has no decor before it on Android 11): the home screens draw their own clock; a swipe from the top shows the bars briefly
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val handed = bookFrom(intent)
        if (handed != null) { reading = handed; return }
        homePressed = true
        reading = null; settings = false // Home goes to the library, as on an e-reader
    }

    override fun onResume() {
        super.onResume()
        val modes = Records.modes(this)
        mode = modes.active
        batteryLevel = battery()
        val picked = mode?.let { modes.apps(it) }.orEmpty()
        val pm = packageManager
        val all = launcherApps(this)
        fun app(p: Pair<String, String>) = App(p.first, p.second, icons.getOrPut(p.first) { pm.getApplicationIcon(p.first).toBitmap((72 * resources.displayMetrics.density).toInt()) })
        val readerPkg = modes.reader(all.map { it.first })
        reader = all.firstOrNull { it.first == readerPkg }?.let(::app)
        apps = all.filter { it.first in picked && (mode != Profile.E_READER || it.first != readerPkg) }.map(::app)
        if (reading != null) homePressed = false // a book was handed over: show it, not the mode's single app
        if (mode == Profile.KIOSK && apps.size == 1 && homePressed) open(apps[0].pkg)
        // an e-reader has no launcher: Home is the reading app itself; Back from it shows this screen, the way out
        if (mode == Profile.E_READER && readerPkg != BUILT_IN_READER && homePressed) open(readerPkg)
        homePressed = false
        resumes++
        applyScreen()
    }

    // the volume keys turn the page while a book is open, as the buttons of an e-reader do
    private fun turnsPage(key: Int) = (key == KeyEvent.KEYCODE_VOLUME_DOWN || key == KeyEvent.KEYCODE_VOLUME_UP) && reading != null && pageTurner != null && Records.shelf(this).volumeKeys
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (turnsPage(keyCode)) { pageTurner?.invoke(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN); true } else super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = turnsPage(keyCode) || super.onKeyUp(keyCode, event) // or the phone still beeps

    /**
     * The file of a book another app asks to have opened ("Open with"), or null for any other intent.
     * A file:// link names it. A content:// link does not, but with All files access the real file can be read
     * all the same once its path is known, and the open descriptor tells; failing that, the book is copied in.
     */
    private fun bookFrom(intent: Intent): String? {
        val uri = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW } ?: return null
        if (uri.scheme == "file") return uri.path
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd -> Os.readlink("/proc/self/fd/${fd.fd}").takeIf { File(it).canRead() } }
                // shortcut: a copy is another file to the reader, so its place is kept apart from the original's
                ?: File(cacheDir, "opened-${uri.toString().hashCode()}.${kind(uri)}").also { copy -> contentResolver.openInputStream(uri)!!.use { from -> copy.outputStream().use { from.copyTo(it) } } }.path
        } catch (e: Exception) { null } // no longer there, or not ours to read: nothing to open
    }

    /** The kind of book behind a content:// link, as a file extension: from its name when that tells, else from its type. */
    private fun kind(uri: Uri): String {
        val type = contentResolver.getType(uri).orEmpty()
        return listOfNotNull(uri.lastPathSegment?.substringAfterLast('.', ""), MimeTypeMap.getSingleton().getExtensionFromMimeType(type)).map { it.lowercase() }.firstOrNull { it in BOOK_TYPES }
            ?: when { "fictionbook" in type -> "fb2"; "comicbook" in type || "cbz" in type -> "cbz"; else -> "epub" }
    }

    /**
     * The reader's own choices about the screen, applied to this window only and only for MonoMode's own reader:
     * grey (drawn through a filter that removes all colour; the phone-wide grey is the mode's own option),
     * brightness, orientation, and staying on while a book is open.
     */
    private fun applyScreen() {
        val shelf = Records.shelf(this)
        val own = reading != null || (mode == Profile.E_READER && reader == null)
        val grey = own && !shelf.colour
        window.decorView.setLayerType(if (grey) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, if (grey) Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) } else null)
        window.attributes = window.attributes.apply { screenBrightness = (if (own) shelf.brightness else null) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        if (own && reading != null && shelf.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = when (if (own) shelf.orientation else "auto") {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** Opens [b] in KOReader at the page it was left on; KOReader alone if it turns the book down. */
    private fun open(b: Book) = try {
        startActivity(
            Intent(Intent.ACTION_VIEW).setPackage(KOREADER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // KOReader's filter wants a type; octet-stream is on its list for suffixes Android does not know
                .setDataAndType(Uri.fromFile(File(b.path)), MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(b.path).extension.lowercase()) ?: "application/octet-stream")
        )
    } catch (e: ActivityNotFoundException) { open(KOREADER) }

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

    /**
     * E-reader with MonoMode's own reader: the home screen is the library itself, as on a Kindle or Kobo, with no
     * launcher in between. Under the books: the other apps chosen for the mode, and the way back to MonoMode.
     * A book opens in place, on this same screen: a separate screen would start with Android's app-opening animation.
     */
    @Composable
    private fun ReaderHome() = AppTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val path = reading
            // outside E-reader mode there is no library behind a book that was handed over: closing it leaves
            if (path != null) key(path) { Reading(path, ::applyScreen) { reading = null; resumes++; applyScreen(); if (mode != Profile.E_READER) finish() } }
            // the other apps and MonoMode live in Settings, not under the books: a growing library would push them out of reach
            else if (settings) ReaderSettings(::applyScreen, { settings = false }) {
                if (apps.isNotEmpty()) Text(getString(R.string.other_apps), Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                for (a in apps) Text(a.label, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open(a.pkg) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                Text(getString(R.string.monomode), Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { openMonoMode() }.padding(vertical = 8.dp)) {
                    Text(getString(R.string.open_monomode), style = MaterialTheme.typography.bodyLarge)
                    Text(getString(R.string.leave_e_reader_or_change), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text("$batteryLevel%", style = MaterialTheme.typography.labelLarge)
                    IconButton({ settings = true }) { Icon(Icons.Outlined.Settings, getString(R.string.settings)) }
                }
                Library(resumes) { reading = it; applyScreen() }
            }
        }
    }

    /** With another reading app chosen, the screen behind it. Kindle/Kobo style: paper white, black serif text, no colour or motion, so it suits the grayscale screen. */
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
                Text("$batteryLevel%", color = ink, fontFamily = serif, fontSize = 16.sp)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ink)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = ink, fontFamily = serif, fontSize = 18.sp)
            Spacer(Modifier.height(40.dp))
            if (main != null) Row(
                Modifier.fillMaxWidth().border(2.dp, ink).clickable { book?.let { open(it) } ?: open(main.pkg) }.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val b = book
                // the book's cover, else an outline, like the cover slot on a Kobo or Kindle home screen
                cover?.let { Image(it, null, Modifier.size(56.dp, 80.dp).border(1.5.dp, ink), contentScale = ContentScale.Crop) }
                    ?: Box(Modifier.size(56.dp, 80.dp).border(1.5.dp, ink))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(getString(R.string.continue_reading), color = ink, fontFamily = serif, fontSize = 22.sp, maxLines = 1)
                    if (b != null) Text(b.title, color = ink, fontFamily = serif, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (b?.percent != null) "${b.percent}% · ${main.label}" else main.label, color = ink, fontFamily = serif, fontSize = if (b != null) 15.sp else 18.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            for (a in apps) {
                Text(a.label, Modifier.fillMaxWidth().clickable { open(a.pkg) }.padding(vertical = 14.dp), color = ink, fontFamily = serif, fontSize = 20.sp)
                HorizontalDivider(color = Color.Gray)
            }
            Spacer(Modifier.height(32.dp))
            Text(getString(R.string.monomode), Modifier.heightIn(min = 48.dp).clickable { openMonoMode() }.padding(vertical = 14.dp), color = ink.copy(alpha = 0.7f), fontFamily = serif, fontSize = 14.sp)
        }
    }
}


internal class App(val pkg: String, val label: String, val icon: ImageBitmap)

/** A grid of the chosen apps. The way back to MonoMode is a long press on the clock, so a guest does not stumble out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KioskHome(apps: List<App>, now: Date, battery: Int, onOpen: (String) -> Unit, onMonoMode: () -> Unit) = AppTheme {
    // fewer apps, bigger icons, within normal launcher sizes: app icons are drawn for about 48dp and blur when stretched far past it
    val icon = (96f / kotlin.math.sqrt(apps.size.coerceAtLeast(1).toFloat())).coerceIn(48f, 72f).dp
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                Modifier.combinedClickable(onLongClickLabel = stringResource(R.string.open_monomode), onClick = {}, onLongClick = onMonoMode).padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.weight(1f))
            Text("$battery%", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
        Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        if (apps.isEmpty()) Text(stringResource(R.string.no_apps_chosen_open_monomode), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface)
        LazyVerticalGrid(GridCells.Adaptive(icon + 32.dp), Modifier.weight(1f).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(apps, key = { it.pkg }) { a ->
                Column(Modifier.clickable { onOpen(a.pkg) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // plain tile behind the app's own icon (design A): calm, and hides the white squares of older icons
                    Box(Modifier.size(icon + 16.dp).clip(RoundedCornerShape(24)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Image(a.icon, null, Modifier.size(icon).clip(RoundedCornerShape(22)))
                    }
                    Text(a.label, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // visible but needs a hold, so a guest does not leave kiosk by accident
        Text(
            stringResource(R.string.hold_for_monomode),
            Modifier.align(Alignment.CenterHorizontally).combinedClickable(onLongClickLabel = stringResource(R.string.open_monomode), onClick = {}, onLongClick = onMonoMode).padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Settings > Appearance; "system" follows the phone's light or dark setting. */
object Appearance {
    val options = listOf("system" to R.string.appearance_system, "light" to R.string.appearance_light, "dark" to R.string.appearance_dark)
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
        surfaceContainerLow = Color(0xFF171D1B), surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF303634),
    ) else lightColorScheme(
        primary = Color(0xFF00695F), onPrimary = Color.White, primaryContainer = Color(0xFFCCE8E3), onPrimaryContainer = Color(0xFF00201C),
        secondaryContainer = Color(0xFFCCE8E3), onSecondaryContainer = Color(0xFF00201C),
        background = Color(0xFFF5FBF8), surface = Color(0xFFF5FBF8), onSurface = Color(0xFF171D1B),
        surfaceVariant = Color(0xFFE3EAE7), onSurfaceVariant = Color(0xFF3F4946), outline = Color(0xFF6F7976), outlineVariant = Color(0xFFDDE4E1),
        surfaceContainerLowest = Color.White, surfaceContainer = Color(0xFFE9EFEC),
        surfaceContainerLow = Color(0xFFEFF5F2), surfaceContainerHigh = Color(0xFFE3EAE7), surfaceContainerHighest = Color(0xFFDDE4E1),
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
/** App name, or the package name for apps Android does not let us see. */
fun appLabel(ctx: Context, pkg: String): String =
    runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)

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
