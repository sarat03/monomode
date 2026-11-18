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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Surface
import monomode.engine.label
import monomode.engine.readCapped
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.rememberLazyListState
import android.widget.Toast
import android.provider.Settings
import android.os.UserHandle
import android.content.pm.LauncherApps
import androidx.activity.compose.BackHandler
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import monomode.engine.Profile
import java.text.DateFormat
import java.util.Date

/** Set on the intent that opens [HomeActivity] on the reader's library, as when E-reader is switched on. */
const val EXTRA_LIBRARY = "library"

/** The home screen: the everyday launcher, and the e-reader's library, which opens like an app (Kiosk pins its chosen app instead). */
class HomeActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var mode by mutableStateOf<Profile?>(null)
    /** The reading app chosen instead of MonoMode's own reader; null while its own reader is used. */
    private var reader by mutableStateOf<App?>(null)
    /** Every app, name only, for the plain launcher shown when no mode is on. */
    private var everyApp by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var pinned by mutableStateOf<Set<String>>(emptySet())
    /** All apps list open; a Home press goes back to the pinned apps, like any launcher. */
    private var showAll by mutableStateOf(false)
    /** Counts Home presses on home, so the list scrolls back to the top and search clears. */
    private var homeTaps by mutableStateOf(0)
    /** Read on every return to home, so the warning goes as soon as MonoMode turns it off. */
    private var wirelessOn by mutableStateOf(false)
    private val pins by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    /** KOReader's last book and its cover, reread on every return to this screen; null for other reading apps or without All files access. */
    private var book by mutableStateOf<Book?>(null)
    private var cover by mutableStateOf<ImageBitmap?>(null)
    private var resumes by mutableStateOf(0)
    /** The book open in MonoMode's reader, shown in place of the library; null for the library. */
    private var reading by mutableStateOf<String?>(null)
    /** The reader's Settings screen is showing, in place of the library. */
    private var settings by mutableStateOf(false)
    /** The reader opened like an app (E-reader switched on); Home and Back go back to the launcher. */
    private var library by mutableStateOf(false)
    private var now by mutableStateOf(Date())
    private var batteryLevel by mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.load(this)
        // draw under the status and navigation bars and the camera cutout, so the screen colour runs to the edges; the content keeps clear with safeDrawingPadding
        enableEdgeToEdge()
        reading = bookFrom(intent)
        library = intent.getBooleanExtra(EXTRA_LIBRARY, false)
        // a home screen never finishes on Back (that would reveal the phone's usual launcher underneath); Back from the library goes to the launcher
        onBackPressedDispatcher.addCallback(this) { if (library) { library = false; reload() } }
        // KOReader opens a book from a file:// link and reads the file with its own storage access, so nothing is
        // shared through the link; Android's default policy would crash this app for sending one
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        setContent {
            // wake at each new minute, so the clock never lags the status bar's
            LaunchedEffect(Unit) { while (true) { now = Date(); batteryLevel = battery(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
            LaunchedEffect(resumes, reader?.pkg) {
                val found = if (reader?.pkg == KOREADER && Environment.isExternalStorageManager()) withContext(Dispatchers.IO) { koreaderBook(Environment.getExternalStorageDirectory()) } else null
                if (found?.path != book?.path) cover = found?.let { withContext(Dispatchers.IO) { loadCover(it.path) } }
                book = found
            }
            // the reader is an app, not a home screen: shown when opened (E-reader switched on, or a book handed over with "Open with"), else the launcher
            if (reading != null || library) { if (reading != null || reader == null) ReaderHome() else Reader() }
            else AllApps(
                everyApp, pinned, showAll, homeTaps, now, { open(it) }, ::togglePin, { showAll = it }, { openMonoMode() },
                wirelessOn = wirelessOn,
                onAppInfo = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                onUninstall = { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", it, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }
        // full screen, after setContent (the window has no decor before it on Android 11): the home screens draw their own clock; a swipe from the top shows the bars briefly
        window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        getSystemService(LauncherApps::class.java).registerCallback(packages)
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packages)
        super.onDestroy()
    }

    /** Apps installed, removed or updated while home is on screen show up at once, not only on the next return. */
    private val packages = object : LauncherApps.Callback() {
        override fun onPackageRemoved(pkg: String, user: UserHandle) {
            if (pkg in pinned) togglePin(pkg) // uninstalled: drop its pin, so a reinstall starts unpinned like any launcher
            reload()
        }
        override fun onPackageAdded(pkg: String, user: UserHandle) = reload()
        override fun onPackageChanged(pkg: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val handed = bookFrom(intent)
        if (handed != null) { reading = handed; return }
        // Home leaves the reader like any app; E-reader switched on opens its library
        library = intent.getBooleanExtra(EXTRA_LIBRARY, false)
        reading = null; settings = false
        // Home on home: back to the top of the pinned apps with search cleared, as launchers do
        showAll = false
        homeTaps++
    }

    private fun togglePin(pkg: String) {
        pinned = if (pkg in pinned) pinned - pkg else pinned + pkg
        pins.edit().putStringSet("pinned", pinned).apply()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val modes = Records.modes(this)
        // no mode takes over home: the launcher stays, and the e-reader's library opens like an app
        mode = modes.active
        // the reader is full screen and draws its own clock; the everyday launcher keeps the status bar and its notifications
        // a tablet's taskbar is its navigation bar and shows every app, so the reader hides it too; phones keep their gesture bar
        val bars = WindowInsets.Type.statusBars() or (if (resources.configuration.smallestScreenWidthDp >= 600) WindowInsets.Type.navigationBars() else 0)
        window.insetsController?.run { if (reading == null && !library) show(bars) else hide(bars) }
        batteryLevel = battery()
        wirelessOn = Session.isWirelessDebuggingOn(this)
        val picked = mode?.let { modes.apps(it) }.orEmpty()
        val all = launcherApps(this)
        fun app(p: Pair<String, String>) = App(p.first, p.second)
        val readerPkg = modes.reader(all.map { it.first })
        reader = all.firstOrNull { it.first == readerPkg }?.let(::app)
        // the everyday launcher lists MonoMode like any app; the kiosk picker and cleaner, which share launcherApps, must not
        everyApp = (all + (packageName to getString(R.string.app_name))).sortedBy { it.second.lowercase() }
        pinned = pins.getStringSet("pinned", emptySet())!!.toSet()
        apps = all.filter { it.first in picked && it.first != readerPkg }.map(::app)
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
                ?: File(cacheDir, "opened-${uri.toString().hashCode()}.${kind(uri)}").also { copy -> contentResolver.openInputStream(uri)!!.use { from -> copy.writeBytes(from.readCapped() ?: throw java.io.IOException("too big to open")) } }.path
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
        val own = reading != null || (library && reader == null)
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
    // an app can be disabled or removed between drawing the list and the tap; say so instead of crashing home
    private fun open(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch == null || runCatching { startActivity(launch) }.isFailure) {
            Toast.makeText(this, getString(R.string.could_not_open_app), Toast.LENGTH_SHORT).show()
            reload()
        }
    }
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
            // unless the library is open there is nothing behind a book that was handed over: closing it leaves
            if (path != null) key(path) { Reading(path, ::applyScreen) { reading = null; resumes++; applyScreen(); if (!library) finish() } }
            // the other apps live in Settings, not under the books, where a growing library would push them out of reach; MonoMode is in the library's bar
            else if (settings) ReaderSettings(::applyScreen, { settings = false }) {
                if (apps.isNotEmpty()) Text(getString(R.string.other_apps), Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                for (a in apps) Text(a.label, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open(a.pkg) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
            else Box(Modifier.safeDrawingPadding()) {
                Library(resumes, DateFormat.getTimeInstance(DateFormat.SHORT).format(now) + "  ·  $batteryLevel%", ::openMonoMode, { settings = true }) { reading = it; applyScreen() }
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
        Column(Modifier.fillMaxSize().background(paper).safeDrawingPadding().padding(24.dp)
            // a book-width column on tablets, centred on the paper
            .wrapContentWidth().widthIn(max = 640.dp).verticalScroll(rememberScrollState())) {
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


/**
 * The everyday launcher when no mode is on (#23): a clock, your pinned apps, and All apps. Nothing else.
 * MonoMode is one app among them; its modes, tools and settings live inside it, not here.
 * Apps are names only, no icons or badges, so nothing on it pulls for attention.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AllApps(
    apps: List<Pair<String, String>>, pinned: Set<String>, showAll: Boolean, homeTaps: Int, now: Date,
    onOpen: (String) -> Unit, onTogglePin: (String) -> Unit, onShowAll: (Boolean) -> Unit, onMonoMode: () -> Unit,
    onAppInfo: (String) -> Unit = {}, onUninstall: (String) -> Unit = {},
    wirelessOn: Boolean = false,
) = AppTheme {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(homeTaps, showAll) { query = ""; menuFor = null; list.scrollToItem(0) }
    BackHandler(showAll) { onShowAll(false) }
    val shown = if (showAll) apps.filter { it.second.contains(query.trim(), ignoreCase = true) } else apps.filter { it.first in pinned }

    @Composable
    fun AppRow(pkg: String, label: String) = Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .combinedClickable(onLongClickLabel = stringResource(R.string.app_options), onClick = { onOpen(pkg) }, onLongClick = { menuFor = pkg })
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // home: big names, the way minimal launchers do; All apps stays compact for scanning a long list
            Text(label, Modifier.weight(1f), style = if (showAll) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showAll && pkg in pinned) Text(stringResource(R.string.pinned), color = faint, style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(menuFor == pkg, { menuFor = null }) {
            DropdownMenuItem({ Text(stringResource(if (pkg in pinned) R.string.unpin else R.string.pin)) }, { menuFor = null; onTogglePin(pkg) })
            DropdownMenuItem({ Text(stringResource(R.string.app_info)) }, { menuFor = null; onAppInfo(pkg) })
            DropdownMenuItem({ Text(stringResource(R.string.uninstall)) }, { menuFor = null; onUninstall(pkg) })
        }
    }

    // Surface, not a bare background: it also sets the text colour, which the clock and app names take; without it they drew black in dark theme
    Surface(Modifier.fillMaxSize()) { LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), list,
    ) {
        if (showAll) {
            // MonoMode's page header: back arrow and title
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ onShowAll(false) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(R.string.all_apps), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_apps)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    // Enter opens the top match, so typing a few letters and Go is enough
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { onOpen(it.first) } }),
                )
            }
            items(shown, key = { it.first }) { (pkg, label) -> AppRow(pkg, label) }
            if (shown.isEmpty()) item { Text(stringResource(R.string.no_apps_match), Modifier.padding(16.dp), color = faint) }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 32.dp, bottom = 24.dp)) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), style = MaterialTheme.typography.displayLarge)
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = faint, style = MaterialTheme.typography.titleMedium)
            }
        }
        // the one exception to "MonoMode stays inside MonoMode": while Wireless debugging is on, a paired computer can control this phone
        if (wirelessOn) item {
            val w = warnTone().fg
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = stringResource(R.string.open_monomode), onClick = onMonoMode).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Warning, null, Modifier.size(18.dp), tint = w)
                Text(stringResource(R.string.wireless_debugging_is_on), color = w, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // names only, no cards behind them: the pinned apps are the quietest thing on the screen
        if (shown.isEmpty()) item { Text(stringResource(R.string.no_pinned_apps), Modifier.padding(8.dp), color = faint, style = MaterialTheme.typography.bodyMedium) }
        items(shown, key = { it.first }) { (pkg, label) -> AppRow(pkg, label) }
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onShowAll(true) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.all_apps), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text("${apps.size}", color = faint, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = faint)
            }
        }
        // the way into MonoMode, worded as what it is for; MonoMode itself is also in All apps
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = stringResource(R.string.open_monomode), onClick = onMonoMode).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.modes), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = faint)
            }
        }
    } }
}

/** A small heading over a group of cards, as on every MonoMode page. */
@Composable
internal fun Section(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(start = 8.dp, top = 8.dp).semantics { heading() },
    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

internal val Profile.whatRes get() = when (this) { Profile.KIOSK -> R.string.only_the_apps_you_choose; Profile.E_READER -> R.string.a_calm_grey_screen_for }
internal val Profile.icon get() = when (this) { Profile.KIOSK -> AppIcons.Grid; Profile.E_READER -> AppIcons.Book }

internal class App(val pkg: String, val label: String)

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
