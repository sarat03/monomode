package io.github.sarat03.monomode

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import monomode.engine.Epub
import monomode.engine.Look
import monomode.engine.Place
import monomode.engine.Shelf
import java.text.DateFormat
import java.util.Date
import monomode.engine.epubCover
import monomode.engine.findEpubs
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URLConnection
import kotlin.math.abs
import kotlin.math.roundToInt

/** A book's cover, scaled down for a thumbnail; null when it has none. Reads the file: call off the main thread. */
internal fun loadCover(path: String): ImageBitmap? = epubCover(File(path))?.let { bytes ->
    // covers are often print size; a thumbnail needs a fraction of that
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, size.outHeight / 400) })?.asImageBitmap()
}

@Composable
private fun <T> Options(title: Int, options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) = Column {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) FilterChip(selected == value, { onPick(value) }, { Text(stringResource(label)) })
    }
}

/** The page options every e-reader keeps under "Aa": theme, text size, font, line spacing and margins. */
@Composable
internal fun LookOptions(look: Look, set: (Look) -> Unit) {
    Options(R.string.theme, listOf("day" to R.string.theme_day, "sepia" to R.string.theme_sepia, "night" to R.string.theme_night), look.theme) { set(look.copy(theme = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.text_size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val smaller = stringResource(R.string.smaller_text); val larger = stringResource(R.string.larger_text)
        OutlinedButton({ set(look.copy(size = (look.size - 10).coerceAtLeast(70))) }, Modifier.semantics { contentDescription = smaller }) { Text("A−", fontSize = 14.sp) }
        Text("${look.size}%", Modifier.width(64.dp), textAlign = TextAlign.Center)
        OutlinedButton({ set(look.copy(size = (look.size + 10).coerceAtMost(250))) }, Modifier.semantics { contentDescription = larger }) { Text("A+", fontSize = 20.sp) }
    }
    Options(R.string.font, listOf("serif" to R.string.font_serif, "sans-serif" to R.string.font_sans, "" to R.string.font_book), look.font) { set(look.copy(font = it)) }
    Options(R.string.line_spacing, listOf(130 to R.string.compact, 150 to R.string.normal, 180 to R.string.wide), look.spacing) { set(look.copy(spacing = it)) }
    Options(R.string.margins, listOf(3 to R.string.narrow, 6 to R.string.normal, 10 to R.string.wide), look.margin) { set(look.copy(margin = it)) }
    Column {
        Text(stringResource(R.string.text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(look.bold, { set(look.copy(bold = !look.bold)) }, { Text(stringResource(R.string.bold)) })
            FilterChip(look.justify, { set(look.copy(justify = !look.justify)) }, { Text(stringResource(R.string.justify)) })
            FilterChip(look.hyphens, { set(look.copy(hyphens = !look.hyphens)) }, { Text(stringResource(R.string.hyphenate)) })
            FilterChip(look.indent, { set(look.copy(indent = !look.indent)) }, { Text(stringResource(R.string.book_paragraphs)) })
        }
    }
}

/** Brightness for the reader alone; "Phone's own" hands it back. [onScreen] applies it. */
@Composable
internal fun Brightness(shelf: Shelf, onScreen: () -> Unit) = Column {
    var level by remember { mutableStateOf(shelf.brightness) }
    val name = stringResource(R.string.brightness)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(level == null, { level = null; shelf.brightness = null; onScreen() }, { Text(stringResource(R.string.phones_own)) })
    }
    // never fully dark: the slider itself must stay visible
    Slider(level ?: 0.5f, { level = it.coerceAtLeast(0.02f); shelf.brightness = level; onScreen() }, Modifier.semantics { contentDescription = name })
}

@Composable
private fun SwitchRow(title: Int, note: Int?, on: Boolean, enabled: Boolean = true, set: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = set), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, null, enabled = enabled)
    }

/**
 * The reader's own settings, reached from the library: how the page looks, colour or grey, and under them [more]
 * (the other apps and the way back to MonoMode). [onScreen] runs after a choice about the screen has been saved.
 */
@Composable
internal fun ReaderSettings(onScreen: () -> Unit, onBack: () -> Unit, more: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    BackHandler(onBack = onBack)
    val dark = isDark()
    var look by remember { mutableStateOf(shelf.look(dark)) }
    var colour by remember { mutableStateOf(shelf.colour) }
    // E-reader mode's "Grayscale screen" option greys the whole phone; this app cannot switch that back on its own
    val systemGrey = remember {
        // colour correction on, and set to monochrome (0): other correction types keep colour
        Settings.Secure.getInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 0) == 1 && Settings.Secure.getInt(ctx.contentResolver, "accessibility_display_daltonizer", -1) == 0
    }
    @Composable fun Section(text: Int) = Text(stringResource(text), Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(R.string.page)
            LookOptions(look) { look = it; shelf.setLook(it) }
            Section(R.string.screen)
            SwitchRow(R.string.colour, if (systemGrey) R.string.colour_is_off_for_the_phone else R.string.off_shows_everything_in_grey, colour && !systemGrey, enabled = !systemGrey) { colour = it; shelf.colour = it; onScreen() }
            Brightness(shelf, onScreen)
            var orientation by remember { mutableStateOf(shelf.orientation) }
            Options(R.string.orientation, listOf("auto" to R.string.orientation_auto, "portrait" to R.string.orientation_portrait, "landscape" to R.string.orientation_landscape), orientation) { orientation = it; shelf.orientation = it; onScreen() }
            Section(R.string.reading)
            var keepOn by remember { mutableStateOf(shelf.keepOn) }
            SwitchRow(R.string.keep_the_screen_on, R.string.while_a_book_is_open, keepOn) { keepOn = it; shelf.keepOn = it }
            var volumeKeys by remember { mutableStateOf(shelf.volumeKeys) }
            SwitchRow(R.string.volume_keys_turn_pages, null, volumeKeys) { volumeKeys = it; shelf.volumeKeys = it }
            var status by remember { mutableStateOf(shelf.status) }
            Column {
                Text(stringResource(R.string.line_at_the_foot), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((key, label) in listOf("clock" to R.string.status_clock, "title" to R.string.status_chapter, "battery" to R.string.status_battery, "left" to R.string.status_pages_left, "page" to R.string.status_page, "percent" to R.string.status_percent))
                        FilterChip(key in status, { status = if (key in status) status - key else status + key; shelf.status = status }, { Text(stringResource(label)) })
                }
            }
            more()
        }
    }
}

private class Entry(val path: String, val title: String, val author: String, val place: Place?)

/**
 * Every EPUB on the phone, as e-reader apps lay a library out: the book being read on top under
 * "Continue reading", then the rest with a search box and a sort order.
 * Rescans whenever [resumes] changes.
 */
@Composable
internal fun Library(resumes: Int, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    val access = remember(resumes) { Environment.isExternalStorageManager() }
    var books by remember { mutableStateOf<List<Entry>?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(shelf.sort) }
    // shortcut: rescans on every return; fine for hundreds of books, cache the list if a library gets slow
    LaunchedEffect(resumes) {
        if (access) books = withContext(Dispatchers.IO) {
            findEpubs(Environment.getExternalStorageDirectory()).mapNotNull { f ->
                try { Epub(f).use { Entry(f.path, it.title, it.author, shelf.place(f.path)) } } catch (e: IOException) { null }
            }
        }
    }
    val list = books
    val reading = list?.filter { it.place != null }?.maxByOrNull { it.place!!.at }?.takeIf { query.isBlank() }
    val shown = list.orEmpty().filter { it !== reading && (query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true)) }.let { l ->
        when (sort) {
            "title" -> l.sortedBy { it.title.lowercase() }
            "author" -> l.sortedWith(compareBy({ it.author.lowercase() }, { it.title.lowercase() }))
            else -> l.sortedWith(compareByDescending<Entry> { it.place?.at ?: 0L }.thenBy { it.title.lowercase() })
        }
    }
    @Composable fun Heading(text: Int) = Text(stringResource(text), Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    LazyColumn(Modifier.fillMaxSize()) {
        if (reading != null) { item { Heading(R.string.continue_reading) }; item { BookRow(reading, onOpen, big = true) } }
        item { Heading(R.string.library) }
        when {
            !access -> item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.reader_needs_file_access))
                    Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text(stringResource(R.string.allow_file_access)) }
                }
            }
            list == null -> item { Text(stringResource(R.string.looking_for_books), Modifier.padding(16.dp)) }
            list.isEmpty() -> item { Text(stringResource(R.string.no_books_found), Modifier.padding(16.dp)) }
            else -> {
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.search_books)) })
                        Options(R.string.sort_by, listOf("recent" to R.string.sort_recent, "title" to R.string.sort_title, "author" to R.string.sort_author), sort) { sort = it; shelf.sort = it }
                    }
                }
                if (shown.isEmpty() && query.isNotBlank()) item { Text(stringResource(R.string.no_books_match), Modifier.padding(16.dp)) }
                items(shown, key = { it.path }) { BookRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun BookRow(b: Entry, onOpen: (String) -> Unit, big: Boolean = false) {
    val cover by produceState<ImageBitmap?>(null, b.path) { value = withContext(Dispatchers.IO) { loadCover(b.path) } }
    Row(Modifier.fillMaxWidth().clickable { onOpen(b.path) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val slot = (if (big) Modifier.size(98.dp, 140.dp) else Modifier.size(56.dp, 80.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        cover?.let { Image(it, null, slot, contentScale = ContentScale.Crop) } ?: Box(slot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (big) 8.dp else 4.dp)) {
            Text(b.title, style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val p = b.place?.progress
            if (p == null) Text(stringResource(R.string.new_book), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator({ p }, Modifier.weight(1f).height(if (big) 5.dp else 3.dp))
                Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (!big) HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Shows one chapter, laid out in screen-wide columns: a page is one column, and turning a page is a jump of one screen width. */
private class BookView(ctx: Context) : WebView(ctx) {
    /** Place in the chapter (0..1) to show once it has been laid out. */
    var pending: Float? = null
    /** The style sheet added to every chapter; read on a WebView thread. */
    @Volatile var css = ""
    val pages get() = (computeHorizontalScrollRange().toFloat() / width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
    val page get() = scrollX / width.coerceAtLeast(1)
    fun show(page: Int) = scrollTo(page.coerceIn(0, pages - 1) * width, 0)
}

/** Turns the page of the open book, forward or back; the activity calls it for the volume keys. Null while no book is open. */
internal var pageTurner: ((Boolean) -> Unit)? = null

// a made-up address the chapters are served under, so the book's own relative links (styles, pictures) resolve
private const val HOST = "book.invalid"

/** theme -> paper and ink colours */
private val PAPER = mapOf("day" to ("#ffffff" to "#111111"), "sepia" to ("#f4ecd8" to "#3b2f1e"), "night" to ("#121212" to "#d0d0d0"))

/** The page style for a view [w] by [h] CSS pixels. Sizes are given in pixels: vw and vh are not dependable in a WebView. */
private fun css(l: Look, w: Float, h: Float): String {
    val (paper, ink) = PAPER.getValue(l.theme)
    // a wide screen shows two columns, like an open book: lines the full width of a tablet are too long to read
    val columns = if (w > 840) 2 else 1
    val m = w / columns * l.margin / 100
    // a page is exactly one screen: each column plus its gap is the view's width (or half of it), and the body is one view tall
    // the viewport line pins the zoom: without it the WebView carries a zoom over from the last size it had, and grows it on every turn of the phone
    return "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no\"/><style>html{height:${h}px !important;background:$paper !important}" +
        "body{box-sizing:content-box !important;margin:0 !important;padding:24px ${m}px !important;width:auto !important;height:${h - 48}px !important;" +
        "column-width:${w / columns - 2 * m}px !important;column-gap:${2 * m}px !important;column-fill:auto !important;" +
        "background:$paper !important;color:$ink !important;line-height:${l.spacing / 100f} !important}" +
        "p,li,blockquote,div{line-height:inherit !important}" +
        (if (l.font.isNotEmpty()) "body,p,li,div,span,blockquote,td{font-family:${l.font} !important}" else "") +
        (if (l.bold) "body,p,li,div,span,td{font-weight:bold !important}" else "") +
        (if (l.justify) "p,li,div{text-align:justify !important}" else "") +
        (if (l.hyphens) "p,li,div{hyphens:auto !important;-webkit-hyphens:auto !important}" else "") +
        (if (l.indent) "p{text-indent:1.5em !important;margin-top:0 !important;margin-bottom:0 !important}" else "") +
        // a book's own colours are made for white paper
        (if (l.theme != "day") "body *{color:inherit !important;background-color:transparent !important}" else "a{color:inherit !important}") +
        "img,svg,video{max-width:100% !important;max-height:${h - 60}px !important;height:auto;break-inside:avoid}</style>"
}

/**
 * The reading page, as e-reader apps do it: the text alone, in real pages. Tap the right or left third (or
 * swipe) to turn the page; tap the middle for the bars with contents, the slider and the text settings.
 * One chapter at a time is shown in a WebView, straight from the EPUB's zip, with scripts and network off, so a
 * book cannot run code or reach the internet. shortcut: EPUB only; no search, bookmarks or highlights.
 */
@SuppressLint("ClickableViewAccessibility") // taps are a shortcut; the bars offer the same moves as buttons
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Reading(path: String, onScreen: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    var bars by remember { mutableStateOf(false) }
    BackHandler { if (bars) bars = false else onClose() }
    // shortcut: opened on the main thread; it reads only the zip's index and the small package file
    val book = remember { try { Epub(File(path)).takeIf { it.spine.isNotEmpty() } } catch (e: IOException) { null } }
    if (book == null) { Text(stringResource(R.string.cant_open_book), Modifier.safeDrawingPadding().padding(16.dp)); return }

    val dark = isDark()
    val start = remember { shelf.place(path) }
    var chapter by remember { mutableStateOf((start?.chapter ?: 0).coerceIn(book.spine.indices)) }
    /** Where in the chapter to land while it loads; [pages] is 0 until it has been laid out. */
    var target by remember { mutableStateOf(start?.inChapter ?: 0f) }
    var page by remember { mutableStateOf(0) }
    var pages by remember { mutableStateOf(0) }
    var look by remember { mutableStateOf(shelf.look(dark)) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val web = remember { BookView(ctx) }
    var marks by remember { mutableStateOf(shelf.marks(path)) }
    /** Where a jump started (chapter, place in it, and the percent the page showed), offered as "Back to" until the next page turn. */
    var before by remember { mutableStateOf<Triple<Int, Float, String>?>(null) }

    // the end of the page counts as read, so the last page of the book is 100%
    val progress = book.progress(chapter, if (pages == 0) target else (page + 1f) / pages)
    val title = book.toc.firstOrNull { it.second.substringBefore('#') == book.spine[chapter] }?.first.orEmpty()
    val numbered = stringResource(R.string.chapter_d, chapter + 1) // the name of a chapter the contents do not list
    val marked = remember(marks, chapter, page, pages) { pages > 0 && shelf.markOn(path, chapter, page.toFloat() / pages, (page + 1f) / pages) != null }
    fun save() { if (pages > 0) shelf.save(path, chapter, page.toFloat() / pages, book.progress(chapter, (page + 1f) / pages)) }
    fun go(to: Int, at: Float, anchor: String? = null) {
        if (to !in book.spine.indices) return
        chapter = to; target = at; page = 0; pages = 0
        web.pending = if (anchor == null) at else null // an #anchor scrolls by itself
        val density = ctx.resources.displayMetrics.density
        web.css = css(look, web.width / density, web.height / density)
        web.setBackgroundColor(android.graphics.Color.parseColor(PAPER.getValue(look.theme).first))
        web.loadUrl("https://$HOST/" + book.spine[to].split('/').joinToString("/") { Uri.encode(it) } + (anchor?.let { "#$it" } ?: ""))
    }
    fun turn(forward: Boolean) {
        if (pages == 0) return // the chapter is still loading
        val next = web.page + if (forward) 1 else -1
        before = null
        if (next in 0 until web.pages) { web.show(next); page = next; save() } else go(chapter + if (forward) 1 else -1, if (forward) 0f else 1f)
    }
    /** [go], remembering where it started. */
    fun jump(to: Int, at: Float, anchor: String? = null) {
        if (to !in book.spine.indices) return
        before = Triple(chapter, if (pages == 0) target else page.toFloat() / pages, "${(progress * 100).roundToInt()}%")
        go(to, at, anchor)
    }
    fun toggleMark() {
        if (pages == 0) return
        shelf.toggleMark(path, chapter, page.toFloat() / pages, (page + 1f) / pages, progress, title.ifEmpty { numbered })
        marks = shelf.marks(path)
    }
    /** A new look means new page breaks: lay the chapter out again and stay at the same place in it. */
    fun restyle(l: Look) {
        val at = if (pages == 0) target else page.toFloat() / pages
        look = l; shelf.setLook(l); web.settings.textZoom = l.size
        go(chapter, at)
    }

    DisposableEffect(web) {
        web.settings.apply { blockNetworkLoads = true; allowFileAccess = false; allowContentAccess = false; textZoom = look.size; setSupportZoom(false); useWideViewPort = true } // useWideViewPort: obey the viewport line css() adds // JavaScript is off by default
        web.isHorizontalScrollBarEnabled = false; web.isVerticalScrollBarEnabled = false; web.overScrollMode = View.OVER_SCROLL_NEVER
        web.webViewClient = object : WebViewClient() {
            // runs on a WebView thread; ZipFile is safe to share
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val entry = request.url.path.orEmpty().removePrefix("/")
                val bytes = (if (request.url.host == HOST) book.bytes(entry) else null) ?: return WebResourceResponse("text/plain", null, ByteArrayInputStream(ByteArray(0)))
                // the type the book declares: EPUB chapters are XHTML, which a browser misreads as plain HTML
                val type = book.mediaType(entry) ?: URLConnection.guessContentTypeFromName(entry) ?: "application/octet-stream"
                if ("html" !in type) return WebResourceResponse(type, null, ByteArrayInputStream(bytes))
                val text = bytes.decodeToString()
                val head = Regex("</head>", RegexOption.IGNORE_CASE)
                val styled = if (head.containsMatchIn(text)) text.replaceFirst(head, "${web.css}</head>") else text.replaceFirst(Regex("<body", RegexOption.IGNORE_CASE), "${web.css}<body")
                return WebResourceResponse(type, "UTF-8", ByteArrayInputStream(styled.toByteArray()))
            }
            // a link in the text: follow it inside the book, never out of it
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val i = book.spine.indexOf(request.url.path.orEmpty().removePrefix("/"))
                if (request.url.host == HOST && i >= 0) jump(i, 0f, request.url.fragment)
                bars = false // the tap that followed the link may have opened them
                return true
            }
            // the number of pages is known only once the chapter has been drawn
            override fun onPageFinished(view: WebView, url: String) = web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(id: Long) {
                    // the page that holds the place: rounding up instead would creep forward on every new layout
                    // an #anchor scrolls only until its target is just in view at the right edge, mid-page: its page is the next whole one
                    web.show(web.pending?.let { (it * web.pages + 0.001f).toInt() } ?: ((web.scrollX + web.width - 1) / web.width.coerceAtLeast(1)))
                    web.pending = null
                    page = web.page; pages = web.pages; save()
                }
            })
        }
        val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // a tap on a link also lands here first; the link then wins, see shouldOverrideUrlLoading
                when {
                    e.x > web.width * 0.85f && e.y < web.height * 0.12f -> toggleMark() // the top corner, as a page is dog-eared
                    e.x < web.width * 0.3f -> turn(false)
                    e.x > web.width * 0.7f -> turn(true)
                    else -> bars = !bars
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy)) turn(vx < 0)
                return false
            }
        })
        // the page never follows the finger: drags are kept from the WebView, so it only ever rests on a whole page
        web.setOnTouchListener { _, e -> taps.onTouchEvent(e); e.actionMasked == MotionEvent.ACTION_MOVE }
        // the first load waits for the view's size, which sets the page size; a new size means new page breaks
        web.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) web.post { go(chapter, if (pages == 0) target else page.toFloat() / pages) }
        }
        pageTurner = ::turn
        onDispose { pageTurner = null; save(); book.close(); web.destroy() }
    }

    val (paper, ink) = PAPER.getValue(look.theme).let { Color(android.graphics.Color.parseColor(it.first)) to Color(android.graphics.Color.parseColor(it.second)) }
    val percent = "${(progress * 100).roundToInt()}%"
    Box(Modifier.fillMaxSize().background(paper).safeDrawingPadding()) {
        Column {
            AndroidView({ web }, Modifier.weight(1f).fillMaxWidth())
            // the quiet line every e-reader keeps at the foot of the page
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val status = remember { shelf.status }
                // clock and battery, refreshed twice a minute while they are shown
                val tick by produceState(0, status) { while ("clock" in status || "battery" in status) { delay(30_000); value++ } }
                val left = listOfNotNull(
                    if ("clock" in status) remember(tick) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) } else null,
                    title.takeIf { "title" in status && it.isNotEmpty() },
                ).joinToString("  ·  ")
                val right = listOfNotNull(
                    if ("battery" in status) remember(tick) { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) }?.let { stringResource(R.string.battery_d, it) } else null,
                    if ("left" in status && pages > 0) pluralStringResource(R.plurals.pages_left, pages - page - 1, pages - page - 1) else null,
                    if ("page" in status && pages > 0) stringResource(R.string.page_d_of_d, page + 1, pages) else null,
                    percent.takeIf { "percent" in status },
                ).joinToString("  ·  ")
                Text(left, Modifier.weight(1f), color = ink.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(right, color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        // a bookmarked page wears a ribbon in the corner that was tapped
        if (marked) Box(Modifier.align(Alignment.TopEnd).padding(end = 20.dp).size(14.dp, 30.dp).background(MaterialTheme.colorScheme.primary))
        before?.let { (c, f, was) ->
            FilledTonalButton({ before = null; go(c, f) }, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (bars) 120.dp else 32.dp)) {
                Text(stringResource(R.string.back_to_s, was))
            }
        }
        if (bars) {
            val bar = MaterialTheme.colorScheme.surfaceContainerHigh
            Surface(Modifier.align(Alignment.TopStart).fillMaxWidth(), color = bar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.library)) }
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (book.author.isNotEmpty()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(::toggleMark) { Icon(Icons.Filled.Star, stringResource(if (marked) R.string.remove_bookmark else R.string.add_bookmark), tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                    IconButton({ sheet = "contents" }) { Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.contents)) }
                    val textAndPage = stringResource(R.string.text_and_page)
                    TextButton({ sheet = "look" }, Modifier.semantics { contentDescription = textAndPage }) { Text("Aa", fontSize = 20.sp) }
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = bar) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    var drag by remember { mutableStateOf<Float?>(null) }
                    val place = stringResource(R.string.place_in_the_book)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ jump(chapter - 1, 0f) }, enabled = chapter > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_chapter)) }
                        Slider(drag ?: progress, { drag = it }, Modifier.weight(1f).semantics { contentDescription = place },
                            onValueChangeFinished = { drag?.let { v -> val (c, f) = book.locate(v); jump(c, f) }; drag = null })
                        IconButton({ jump(chapter + 1, 0f) }, enabled = chapter < book.spine.lastIndex) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_chapter)) }
                    }
                    // the place, and a tap on it to type one
                    TextButton({ sheet = "goto" }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.chapter_d_of_d, chapter + 1, book.spine.size) + "  ·  " + (drag?.let { "${(it * 100).roundToInt()}%" } ?: percent) + "  ·  " + stringResource(R.string.go_to))
                    }
                }
            }
        }
    }

    // fully open at once: half open, the lower options sit off screen in landscape
    val whole = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (sheet == "contents") ModalBottomSheet({ sheet = null }, sheetState = whole) {
        var tab by remember { mutableStateOf("contents") }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == "contents", { tab = "contents" }, { Text(stringResource(R.string.contents)) })
            FilterChip(tab == "marks", { tab = "marks" }, { Text(stringResource(R.string.bookmarks) + if (marks.isEmpty()) "" else " (${marks.size})") })
        }
        // a book without a contents list still has its chapters
        val list = book.toc.ifEmpty { book.spine.mapIndexed { i, e -> stringResource(R.string.chapter_d, i + 1) to e } }
        LazyColumn {
            if (tab == "contents") items(list) { (label, to) ->
                val i = book.spine.indexOf(to.substringBefore('#'))
                Text(label, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = i >= 0) { jump(i, 0f, to.substringAfter('#', "").ifEmpty { null }); sheet = null; bars = false }
                    .padding(horizontal = 24.dp, vertical = 12.dp), fontWeight = if (i == chapter) FontWeight.Bold else null)
            }
            else {
                if (marks.isEmpty()) item { Text(stringResource(R.string.no_bookmarks_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(marks) { m ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { jump(m.chapter, m.inChapter); sheet = null; bars = false }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(m.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(m.progress * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (sheet == "goto") {
        var typed by remember { mutableStateOf("") }
        val to = typed.toFloatOrNull()?.takeIf { it in 0f..100f }
        AlertDialog({ sheet = null }, title = { Text(stringResource(R.string.go_to)) },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.percent_0_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton({ val (c, f) = book.locate(to!! / 100); jump(c, f); sheet = null; bars = false }, enabled = to != null) { Text(stringResource(R.string.go)) } },
            dismissButton = { TextButton({ sheet = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (sheet == "look") ModalBottomSheet({ sheet = null }, sheetState = whole) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Brightness(shelf, onScreen)
            LookOptions(look, ::restyle)
            Spacer(Modifier.height(8.dp))
        }
    }
}
