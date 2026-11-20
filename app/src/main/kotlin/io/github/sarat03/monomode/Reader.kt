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
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.SolidColor
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
import monomode.engine.BookInfo
import monomode.engine.openBook
import monomode.engine.groups
import monomode.engine.members
import monomode.engine.onList
import java.time.LocalDate
import android.text.format.Formatter
import monomode.engine.Hit
import monomode.engine.Quote
import monomode.engine.exportQuotes
import monomode.engine.Tap
import monomode.engine.tapZone
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import monomode.engine.Look
import monomode.engine.Shelf
import java.text.DateFormat
import java.util.Date
import monomode.engine.bookCover
import monomode.engine.findBooks
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URLConnection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A book's cover, scaled down for a thumbnail; null when it has none. Reads the file: call off the main thread. */
internal fun loadCover(path: String): ImageBitmap? = if (path.endsWith(".pdf", ignoreCase = true)) pdfCover(path) else bookCover(File(path))?.let { bytes ->
    // covers are often print size; a thumbnail needs a fraction of that
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, size.outHeight / 400) })?.asImageBitmap()
}

@Composable
private fun Label(text: Int) = Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A choice of one among a few, as a row of joined buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Options(title: Int, options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Label(title)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected == value, { onPick(value) }, SegmentedButtonDefaults.itemShape(i, options.size), icon = {}) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** How the text looks, under "Aa": the paper, size, font, spacing, margins and the text switches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TextOptions(look: Look, set: (Look) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(R.string.theme)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((key, label) in listOf("day" to R.string.theme_day, "sepia" to R.string.theme_sepia, "night" to R.string.theme_night)) {
                val (paper, ink) = PAPER.getValue(key).let { Color(android.graphics.Color.parseColor(it.first)) to Color(android.graphics.Color.parseColor(it.second)) }
                val on = look.theme == key
                val shape = MaterialTheme.shapes.medium
                // each paper shows itself, so the choice is plain even on a grey screen
                Column(Modifier.weight(1f).clip(shape).background(paper).border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                    .selectable(on, role = Role.RadioButton) { set(look.copy(theme = key)) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aa", color = ink, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                    Text(stringResource(label), color = ink, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Column {
        Row { Label(R.string.text_size); Spacer(Modifier.weight(1f)); Text("${look.size}%", style = MaterialTheme.typography.labelLarge) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            fun size(to: Int) { val v = to.coerceIn(70, 250); if (v != look.size) set(look.copy(size = v)) }
            val smaller = stringResource(R.string.smaller_text); val larger = stringResource(R.string.larger_text); val name = stringResource(R.string.text_size)
            IconButton({ size(look.size - 10) }, Modifier.semantics { contentDescription = smaller }) { Text("A", fontSize = 14.sp) }
            Slider(look.size.toFloat(), { size((it / 10).roundToInt() * 10) }, Modifier.weight(1f).semantics { contentDescription = name }, valueRange = 70f..250f)
            IconButton({ size(look.size + 10) }, Modifier.semantics { contentDescription = larger }) { Text("A", fontSize = 22.sp) }
        }
    }
    Options(R.string.font, listOf("serif" to R.string.font_serif, "sans-serif" to R.string.font_sans, "" to R.string.font_book), look.font) { set(look.copy(font = it)) }
    val spacing = @Composable { Options(R.string.line_spacing, listOf(130 to R.string.compact, 150 to R.string.normal, 180 to R.string.wide), look.spacing) { set(look.copy(spacing = it)) } }
    val margins = @Composable { Options(R.string.margins, listOf(3 to R.string.narrow, 6 to R.string.normal, 10 to R.string.wide), look.margin) { set(look.copy(margin = it)) } }
    // side by side where there is room; on a phone the labels would be cut
    BoxWithConstraints {
        if (maxWidth >= 560.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(Modifier.weight(1f)) { spacing() }; Box(Modifier.weight(1f)) { margins() } }
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { spacing(); margins() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(R.string.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(look.bold, { set(look.copy(bold = !look.bold)) }, { Text(stringResource(R.string.bold)) })
            FilterChip(look.justify, { set(look.copy(justify = !look.justify)) }, { Text(stringResource(R.string.justify)) })
            FilterChip(look.hyphens, { set(look.copy(hyphens = !look.hyphens)) }, { Text(stringResource(R.string.hyphenate)) })
            FilterChip(look.indent, { set(look.copy(indent = !look.indent)) }, { Text(stringResource(R.string.book_paragraphs)) })
        }
    }
}

@Composable
private fun PageTurning(look: Look, set: (Look) -> Unit) =
    Options(R.string.page_turning, listOf(false to R.string.pages, true to R.string.scrolling), look.scroll) { set(look.copy(scroll = it)) }

@Composable
private fun TapOptions(shelf: Shelf) {
    var taps by remember { mutableStateOf(shelf.taps) }
    Options(R.string.taps_on_the_page, listOf("sides" to R.string.taps_sides, "swapped" to R.string.taps_swapped, "forward" to R.string.taps_forward), taps) { taps = it; shelf.taps = it }
}

/**
 * The info line at the foot of the page: shown or not (some readers find it a distraction), and what it shows.
 * [onShown] and [onChange] get each choice once saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusOptions(shelf: Shelf, onShown: (Boolean) -> Unit = {}, onChange: (Set<String>) -> Unit = {}) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    var shown by remember { mutableStateOf(shelf.infoLine) }
    var status by remember { mutableStateOf(shelf.status) }
    SwitchRow(R.string.info_line, R.string.info_line_note, shown) { shown = it; shelf.infoLine = it; onShown(it) }
    if (shown) {
        Label(R.string.line_at_the_foot)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("clock" to R.string.status_clock, "title" to R.string.status_chapter, "battery" to R.string.status_battery, "left" to R.string.status_pages_left, "page" to R.string.status_page, "percent" to R.string.status_percent))
                FilterChip(key in status, { status = if (key in status) status - key else status + key; shelf.status = status; onChange(status) }, { Text(stringResource(label)) })
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
            TextOptions(look) { look = it; shelf.setLook(it) }
            PageTurning(look) { look = it; shelf.setLook(it) }
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
            TapOptions(shelf)
            StatusOptions(shelf)
            more()
        }
    }
}

/** A length of time as "2 h 05 min", "12 min" or "40 s". */
@Composable
private fun span(seconds: Long) = if (seconds >= 3600) stringResource(R.string.time_h_min, seconds / 3600, seconds % 3600 / 60) else if (seconds >= 60) stringResource(R.string.time_min, seconds / 60) else stringResource(R.string.time_s, seconds)

/**
 * The library, as e-reader apps lay one out: a bar with search, sort and view, and settings; the book being read
 * in a card under it; then every book on the phone. One row of chips narrows it to a reading list, and its "Browse"
 * chip groups it by author, series, collection or folder. Holding a book (or its ⋮) opens its details.
 * [clock] is the time and battery shown in the bar; [onMonoMode] leaves for MonoMode itself, from this screen only. Rescans whenever [resumes] changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Library(resumes: Int, clock: String, onMonoMode: () -> Unit, onSettings: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    val access = remember(resumes) { Environment.isExternalStorageManager() }
    var books by remember { mutableStateOf<List<BookInfo>?>(null) }
    /** Counted up when a book's lists or shelves change or it is deleted, to read the library again. */
    var changed by remember { mutableStateOf(0) }
    var view by remember { mutableStateOf("all") }
    var group by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val searchBooks = stringResource(R.string.search_books)
    var sort by remember { mutableStateOf(shelf.sort) }
    var grid by remember { mutableStateOf(shelf.grid) }
    var detail by remember { mutableStateOf<BookInfo?>(null) }
    var menu by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    val root = Environment.getExternalStorageDirectory()
    // shortcut: rescans on every return; fine for hundreds of books, cache the list if a library gets slow
    LaunchedEffect(resumes, changed) {
        if (access) books = withContext(Dispatchers.IO) {
            findBooks(root).mapNotNull { f ->
                try { openBook(f).use { BookInfo(f.path, it.title, it.author, it.series, it.seriesIndex, shelf.favourite(f.path), shelf.status(f.path), shelf.collectionsOf(f.path), shelf.place(f.path)) } } catch (e: IOException) { null }
            }
        }
    }
    BackHandler(query.isNotEmpty() || group != null || view != "all") { if (query.isNotEmpty()) query = "" else if (group != null) group = null else view = "all" }

    val list = books
    val grouping = view in setOf("authors", "series", "collections", "folders")
    val pool = if (list == null) emptyList() else if (grouping) group?.let { members(list, view, it) }.orEmpty() else onList(list, view)
    val reading = list?.filter { it.place != null }?.maxByOrNull { it.place!!.at }?.takeIf { view == "all" && query.isBlank() }
    val shown = pool.filter { it !== reading && (query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true)) }.let { l ->
        when (if (view == "series") "series" else sort) {
            "series" -> l // already in the order of the series
            "title" -> l.sortedBy { it.title.lowercase() }
            "author" -> l.sortedWith(compareBy({ it.author.lowercase() }, { it.title.lowercase() }))
            else -> l.sortedWith(compareByDescending<BookInfo> { it.place?.at ?: 0L }.thenBy { it.title.lowercase() })
        }
    }
    /** A group's name as shown: folders without the storage root, a missing author named as such. */
    @Composable fun named(name: String) = when { view == "folders" -> name.removePrefix(root.path).ifEmpty { "/" }; name.isEmpty() -> stringResource(R.string.unknown_author); else -> name }
    @Composable fun Plain(text: Int) = Text(stringResource(text), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable fun Check(on: Boolean) { if (on) Icon(Icons.Filled.Check, null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // MonoMode's own mark: the way out of the mode, or to change it
            IconButton(onMonoMode) { Icon(painterResource(R.drawable.ic_launcher_monochrome), stringResource(R.string.open_monomode), Modifier.size(40.dp)) }
            IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
        }
        if (!list.isNullOrEmpty()) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // a short field of its own (a Material one is 56dp tall), with the sort and view menu beside it
            val line = MaterialTheme.colorScheme.outline
            BasicTextField(query, { query = it }, Modifier.weight(1f).height(40.dp).semantics { contentDescription = searchBooks }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxSize().border(1.dp, line, CircleShape).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f).padding(start = 8.dp)) {
                            if (query.isEmpty()) Text(searchBooks, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            inner()
                        }
                        if (query.isNotEmpty()) IconButton({ query = "" }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.close_search), Modifier.size(18.dp)) }
                    }
                })
            Box {
                IconButton({ menu = true }) { Icon(AppIcons.Sort, stringResource(R.string.sort_and_view)) }
                DropdownMenu(menu, { menu = false }) {
                    @Composable fun Title(text: Int) = Text(stringResource(text), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Title(R.string.sort_by)
                    for ((key, label) in listOf("recent" to R.string.sort_recent, "title" to R.string.sort_title, "author" to R.string.sort_author))
                        DropdownMenuItem({ Text(stringResource(label)) }, { sort = key; shelf.sort = key; menu = false }, trailingIcon = { Check(sort == key) })
                    HorizontalDivider()
                    Title(R.string.show_as)
                    for ((key, label) in listOf(false to R.string.as_list, true to R.string.as_grid))
                        DropdownMenuItem({ Text(stringResource(label)) }, { grid = key; shelf.grid = key; menu = false }, trailingIcon = { Check(grid == key) })
                    HorizontalDivider()
                    DropdownMenuItem({ Text(stringResource(R.string.statistics)) }, { view = "stats"; group = null; menu = false })
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
        val columns = (maxWidth / 132.dp).toInt().coerceAtLeast(3)
        LazyColumn(Modifier.fillMaxSize()) {
            if (reading != null) item { ContinueCard(reading, onOpen) { detail = reading } }
            if (!access) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.reader_needs_file_access))
                    Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text(stringResource(R.string.allow_file_access)) }
                }
            }
            else if (list == null) item { Plain(R.string.looking_for_books) }
            else if (list.isEmpty()) item { Plain(R.string.no_books_found) }
            else {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((key, label) in listOf("all" to R.string.all_books, "toread" to R.string.to_read, "favourites" to R.string.favourites, "read" to R.string.have_read))
                            FilterChip(view == key, { view = key; group = null }, { Text(if (key == "all") stringResource(R.string.all_d, list.size) else stringResource(label)) })
                        val ways = listOf("authors" to R.string.authors, "series" to R.string.series, "collections" to R.string.collections, "folders" to R.string.folders)
                        Box {
                            FilterChip(grouping, { browse = true }, { Text(stringResource(ways.firstOrNull { it.first == view }?.second ?: R.string.browse)) }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
                            DropdownMenu(browse, { browse = false }) {
                                for ((key, label) in ways) DropdownMenuItem({ Text(stringResource(label)) }, { view = key; group = null; browse = false }, trailingIcon = { Check(view == key) })
                            }
                        }
                    }
                }
                if (view == "stats") {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ view = "all" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                            Text(stringResource(R.string.statistics), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    statistics(shelf, list)
                }
                else if (grouping && group == null) {
                    val found = groups(list, view)
                    if (found.isEmpty()) item { Plain(R.string.nothing_here_yet) }
                    items(found, key = { it.first }) { (name, count) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { group = name }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(named(name), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                else {
                    group?.let { name ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ group = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                                Text(named(name), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (view == "collections") TextButton({ shelf.removeCollection(name); group = null; changed++ }) { Text(stringResource(R.string.remove_collection)) }
                            }
                        }
                    }
                    if (shown.isEmpty()) item { Plain(if (query.isNotBlank()) R.string.no_books_match else R.string.nothing_here_yet) }
                    if (grid) items(shown.chunked(columns)) { row ->
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            for (b in row) BookCell(b, onOpen, { detail = b }, Modifier.weight(1f))
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    else items(shown, key = { it.path }) { b -> BookRow(b, onOpen, { detail = b }) }
                }
            }
        }
        }
    }
    detail?.let { b -> BookDetails(b, shelf, onOpen, { changed++ }) { detail = null } }
}

/** Reading time: today, the last week and in all, then day by day and book by book. */
private fun LazyListScope.statistics(shelf: Shelf, books: List<BookInfo>) {
    val days = shelf.timeByDay()
    val perBook = books.map { it to shelf.timeRead(it.path) }.filter { it.second > 0 }.sortedByDescending { it.second }
    item {
        val today = LocalDate.now()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (days.isEmpty()) Text(stringResource(R.string.no_reading_time_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((label, seconds) in listOf(
                R.string.today to (days[today.toString()] ?: 0L),
                R.string.last_7_days to days.filterKeys { it > today.minusDays(7).toString() }.values.sum(),
                R.string.all_time to days.values.sum(),
            )) Row { Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Text(span(seconds), style = MaterialTheme.typography.bodyLarge) }
        }
    }
    if (days.isNotEmpty()) item { Text(stringResource(R.string.by_day), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
    val most = days.values.maxOrNull() ?: 1L
    items(days.entries.reversed().take(14)) { (day, seconds) ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(day, Modifier.width(104.dp), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator({ seconds.toFloat() / most }, Modifier.weight(1f).height(6.dp))
            Text(span(seconds), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
    if (perBook.isNotEmpty()) item { Text(stringResource(R.string.by_book), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
    items(perBook) { (b, seconds) ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(b.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(span(seconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The small print under a book's title: its place in a series, and the lists it is on. */
@Composable
private fun tags(b: BookInfo) = listOfNotNull(
    b.series.takeIf { it.isNotEmpty() }?.let { s -> b.seriesIndex?.let { stringResource(R.string.series_s_d, s, if (it % 1 == 0f) it.toInt().toString() else it.toString()) } ?: s },
    if (b.favourite) stringResource(R.string.favourite) else null,
    when (b.status) { "toread" -> stringResource(R.string.to_read); "read" -> stringResource(R.string.have_read); else -> null },
).joinToString("  ·  ")

/** The book being read, on top of the library, with a button straight back into it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(b: BookInfo, onOpen: (String) -> Unit, onMore: () -> Unit) {
    val cover by produceState<ImageBitmap?>(null, b.path) { value = withContext(Dispatchers.IO) { loadCover(b.path) } }
    val details = stringResource(R.string.book_details)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.combinedClickable(onLongClickLabel = details, onLongClick = onMore) { onOpen(b.path) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val slot = Modifier.size(84.dp, 120.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            cover?.let { Image(it, null, slot, contentScale = ContentScale.Crop) } ?: Box(slot)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(b.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val p = b.place?.progress ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator({ p }, Modifier.weight(1f).height(4.dp))
                    Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button({ onOpen(b.path) }) { Text(stringResource(R.string.continue_book)) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onMore) { Icon(Icons.Filled.MoreVert, details) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(b: BookInfo, onOpen: (String) -> Unit, onMore: () -> Unit) {
    val cover by produceState<ImageBitmap?>(null, b.path) { value = withContext(Dispatchers.IO) { loadCover(b.path) } }
    val details = stringResource(R.string.book_details)
    Row(Modifier.fillMaxWidth().combinedClickable(onLongClickLabel = details, onLongClick = onMore) { onOpen(b.path) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val slot = Modifier.size(56.dp, 80.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        cover?.let { Image(it, null, slot, contentScale = ContentScale.Crop) } ?: Box(slot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tags(b).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val p = b.place?.progress
            if (p == null) Text(stringResource(R.string.new_book), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator({ p }, Modifier.weight(1f).height(3.dp))
                Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onMore) { Icon(Icons.Filled.MoreVert, details) }
    }
    HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** A book in the cover grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(b: BookInfo, onOpen: (String) -> Unit, onMore: () -> Unit, modifier: Modifier) {
    val cover by produceState<ImageBitmap?>(null, b.path) { value = withContext(Dispatchers.IO) { loadCover(b.path) } }
    Column(modifier.combinedClickable(onLongClickLabel = stringResource(R.string.book_details), onLongClick = onMore) { onOpen(b.path) }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val slot = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(MaterialTheme.shapes.small).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
        // a book without a cover shows its title where the cover would be, on a plain tinted card
        cover?.let { Image(it, null, slot, contentScale = ContentScale.Crop) }
            ?: Box(slot.background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(10.dp), contentAlignment = Alignment.Center) { Text(b.title, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis) }
        b.place?.progress?.let { p -> LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(3.dp)) }
        Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Everything about one book, and what can be done with it: lists, collections, opening, deleting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetails(b: BookInfo, shelf: Shelf, onOpen: (String) -> Unit, onChanged: () -> Unit, onClose: () -> Unit) = ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    val ctx = LocalContext.current
    val file = File(b.path)
    val cover by produceState<ImageBitmap?>(null, b.path) { value = withContext(Dispatchers.IO) { loadCover(b.path) } }
    val description by produceState("", b.path) { value = withContext(Dispatchers.IO) { try { openBook(file).use { it.description } } catch (e: IOException) { "" } } }
    var favourite by remember { mutableStateOf(b.favourite) }
    var status by remember { mutableStateOf(b.status) }
    var names by remember { mutableStateOf(shelf.collections()) }
    var mine by remember { mutableStateOf(shelf.collectionsOf(b.path)) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val slot = Modifier.size(98.dp, 140.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            cover?.let { Image(it, null, slot, contentScale = ContentScale.Crop) } ?: Box(slot)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(b.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                if (b.author.isNotEmpty()) Text(b.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                tags(b.copy(favourite = false, status = "")).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                b.place?.let { Text("${(it.progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium) }
                shelf.timeRead(b.path).takeIf { it > 0 }?.let { Text(stringResource(R.string.time_read_s, span(it)), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button({ onClose(); onOpen(b.path) }) { Text(stringResource(R.string.open)) }
            FilterChip(favourite, { favourite = !favourite; shelf.setFavourite(b.path, favourite); onChanged() }, { Text(stringResource(R.string.favourite)) })
        }
        Options(R.string.reading_list, listOf("" to R.string.status_none, "toread" to R.string.to_read, "read" to R.string.have_read), status) { status = it; shelf.setStatus(b.path, it); onChanged() }
        Column {
            Text(stringResource(R.string.collections), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in names) FilterChip(name in mine, { shelf.setInCollection(name, b.path, name !in mine); mine = shelf.collectionsOf(b.path); onChanged() }, { Text(name) })
            }
            var fresh by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fresh, { fresh = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.new_collection)) })
                TextButton({ shelf.setInCollection(fresh.trim(), b.path, true); names = shelf.collections(); mine = shelf.collectionsOf(b.path); fresh = ""; onChanged() }, enabled = fresh.isNotBlank()) { Text(stringResource(R.string.add)) }
            }
        }
        if (description.isNotEmpty()) Text(description)
        Text("${file.name}  ·  ${Formatter.formatShortFileSize(ctx, file.length())}\n${file.parent}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ deleting = true }) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) }
    }
    // shortcut: what the reader remembers about a deleted book (place, bookmarks, time) is kept, in case the file comes back
    if (deleting) AlertDialog({ deleting = false }, text = { Text(stringResource(R.string.delete_this_book)) },
        confirmButton = { TextButton({ file.delete(); onChanged(); onClose() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ deleting = false }) { Text(stringResource(android.R.string.cancel)) } })
}

/**
 * Shows one chapter. In pages, it is laid out in screen-wide columns: a page is one screen of them, and turning a page
 * is a jump of one page's measured width ([advance]). In [scroll], it is one long page moved with the finger, counted in steps of nine
 * tenths of a screen so that "page 3 of 9" means something there too.
 */
private class BookView(ctx: Context) : WebView(ctx) {
    /** Place in the chapter (0..1) to show once it has been laid out. */
    var pending: Float? = null
    /** The style sheet added to every chapter; read on a WebView thread. */
    @Volatile var css = ""
    var scroll = false
    /** Runs for "Highlight" in the menu that a long press on the text brings up. */
    var onHighlight: () -> Unit = {}

    /**
     * The width of one page in device pixels, measured on the laid-out chapter. The WebView rounds its own width in CSS
     * pixels up, so a page can be a pixel or two wider than the view: turning by the view's width would drift the text
     * that much on every page. A fraction, so it never adds up; each page is rounded to whole pixels on its own.
     */
    var advance = 1f
    /** The number of pages, counted by the page itself: the scroll range learns of the end mark (see MEASURE) only a while later. */
    var count = 1
    private val step get() = (height * 9 / 10).coerceAtLeast(1)
    private val last get() = (computeVerticalScrollRange() - height).coerceAtLeast(0)
    val pages get() = if (scroll) (last + step - 1) / step + 1 else count
    // at the very end the last step is a short one
    val page get() = if (!scroll) (scrollX / advance).roundToInt() else if (scrollY >= last) pages - 1 else (scrollY + step / 2) / step
    fun show(page: Int) = page.coerceIn(0, pages - 1).let { if (scroll) scrollTo(0, it * step) else scrollTo((it * advance).roundToInt(), 0) }
    /** The page that holds the point [xy] ("[x,y]" in the page's own pixels, from this app's script); null for anything else. */
    fun pageAt(xy: String, density: Float): Int? = Regex("-?[0-9.]+").findAll(xy).map { it.value.toFloat() * density }.toList().takeIf { it.size == 2 }?.let { (x, y) -> (if (scroll) y / step else x / advance).toInt() }

    // the system's own menu for selected text (Copy, Share…), with Highlight put first
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = callback.onCreateActionMode(mode, menu).also { menu.add(Menu.NONE, R.string.highlight, 0, R.string.highlight) }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
            if (item.itemId == R.string.highlight) { onHighlight(); mode.finish(); true } else callback.onActionItemClicked(mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) =
            if (callback is ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
    }, type)
}

/**
 * This app's own script, run from outside the page once a chapter has loaded (the book's own scripts stay blocked).
 * Places are counted in characters of the body's text, the same text Epub.search reads.
 */
private const val SCRIPT = """
window.mm = {
  range: function(s, e) {
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT), n, t = 0, r = document.createRange(), a = false;
    while (n = w.nextNode()) {
      var l = n.length;
      if (!a && s < t + l) { r.setStart(n, s - t); a = true; }
      if (a && e <= t + l) { r.setEnd(n, e - t); return r; }
      t += l;
    }
    return null;
  },
  at: function(r) { if (!r) return null; var b = r.getClientRects()[0] || r.getBoundingClientRect(); return [b.left + window.scrollX, b.top + window.scrollY]; },
  paint: function(name, spans) {
    var rs = [];
    for (var i = 0; i < spans.length; i++) { var r = mm.range(spans[i][0], spans[i][1]); if (r) rs.push(r); }
    CSS.highlights.set(name, new Highlight(...rs));
  },
  find: function(q, nth) {
    var t = document.body.textContent.toLowerCase(), at = -1;
    q = q.toLowerCase();
    for (var i = 0; i <= nth; i++) { at = t.indexOf(q, at + 1); if (at < 0) return null; }
    mm.paint('find', [[at, at + q.length]]);
    return mm.at(mm.range(at, at + q.length));
  },
  selection: function() {
    var s = getSelection();
    if (!s.rangeCount || s.isCollapsed) return null;
    var r = s.getRangeAt(0), before = document.createRange();
    before.selectNodeContents(document.body); before.setEnd(r.startContainer, r.startOffset);
    var a = before.toString().length, text = r.toString();
    s.removeAllRanges();
    return [a, a + text.length, text];
  },
  link: function(x, y) { var e = document.elementFromPoint(x, y); return !!(e && e.closest && e.closest('a[href]')); },
  spoken: function(x0, y0) {
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT), n, out = [];
    while (n = w.nextNode()) {
      if (!n.nodeValue.trim()) continue;
      var r = document.createRange(); r.selectNodeContents(n);
      var b = r.getClientRects()[0];
      if (!b) continue;
      var x = b.left + scrollX - x0, y = b.top + scrollY - y0;
      if (x >= 0 && x < innerWidth && y >= 0 && y < innerHeight) out.push(n.nodeValue.trim());
    }
    return out.join(' ');
  }
};
"""

/**
 * Measures a laid-out chapter in pages: returns [the width of a page in device pixels (see BookView.advance), the number of pages]. The last
 * column's right margin is not part of the scroll width, so the last page could not be scrolled to in full and would
 * sit off-centre: a dot at the end of the last page makes room for it. A last page can be one column of two, and a
 * full one lacks a margin (at most a tenth of it): a quarter of a page in between tells them apart.
 */
private const val MEASURE = """
(function() {
  var h = document.documentElement, w = h.getBoundingClientRect().width, n = Math.max(1, Math.ceil(h.scrollWidth / w - 0.25));
  var end = document.createElement('mm-end');
  end.style.cssText = 'position:absolute !important;top:0 !important;left:' + (n * w - 1) + 'px !important;width:1px !important;height:1px !important;display:block !important;background:inherit !important';
  h.appendChild(end);
  return [w * devicePixelRatio, n];
})()
"""

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
    // in whole layout units (1/64 px), so the gap is exactly two paddings and a column plus its gap is exactly the page's width
    val m = (w / columns * l.margin / 100 * 64).roundToInt() / 64f
    val layout = if (l.scroll) "html{background:$paper !important}body{margin:0 !important;padding:24px ${m}px !important;width:auto !important;"
        else "html{height:${h}px !important;margin:0 !important;padding:0 !important;width:auto !important;max-width:none !important;background:$paper !important}" +
            // a book's own width, auto margins or offsets on the body would shift the columns off the page's centre; a column count (not a width) cannot round down to one column fewer
            "body{box-sizing:content-box !important;margin:0 !important;padding:24px ${m}px !important;width:auto !important;max-width:none !important;min-width:0 !important;position:static !important;float:none !important;height:${h - 48}px !important;" +
            "column-count:$columns !important;column-width:auto !important;column-gap:${2 * m}px !important;column-fill:auto !important;"
    // a page is exactly one screen: each column plus its gap is the view's width (or half of it), and the body is one view tall
    // the viewport line pins the zoom: without it the WebView carries a zoom over from the last size it had, and grows it on every turn of the phone
    return "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no\"/><style>$layout" +
        "background:$paper !important;color:$ink !important;line-height:${l.spacing / 100f} !important}" +
        "p,li,blockquote,div{line-height:inherit !important}" +
        (if (l.font.isNotEmpty()) "body,body *{font-family:${l.font} !important}" else "") +
        // a wide table or line of code in another font would run past its column into the next page
        "pre{white-space:pre-wrap !important}pre,table,code{max-width:100% !important}body *{overflow-wrap:break-word}" +
        (if (l.bold) "body,p,li,div,span,td{font-weight:bold !important}" else "") +
        (if (l.justify) "p,li,div{text-align:justify !important}" else "") +
        (if (l.hyphens) "p,li,div{hyphens:auto !important;-webkit-hyphens:auto !important}" else "") +
        (if (l.indent) "p{text-indent:1.5em !important;margin-top:0 !important;margin-bottom:0 !important}" else "") +
        // a book's own colours are made for white paper
        (if (l.theme != "day") "body *{color:inherit !important;background-color:transparent !important}" else "a{color:inherit !important}") +
        "::highlight(quote){background-color:rgba(255,213,79,0.55)}::highlight(find){background-color:rgba(255,145,0,0.7)}" +
        "img,svg,video{max-width:100% !important;max-height:${h - 60}px !important;height:auto;break-inside:avoid}" +
        // a comic's page is its picture alone: the whole screen, not a column of text with margins
        "html body.comic{padding:0 !important;height:${h}px !important;column-width:auto !important;column-count:auto !important}html body.comic img{max-height:${h}px !important;display:block;margin:0 auto}</style>"
}

/**
 * The reading page, as e-reader apps do it: the text alone, in real pages. Tap the right or left third (or
 * swipe) to turn the page; tap the middle for the bars with contents, the slider and the text settings.
 * One chapter at a time is shown in a WebView, straight from the book's file, with the network off and the book's
 * own scripts blocked, so a book cannot run code or reach the internet; only [SCRIPT] runs. A PDF goes to [PdfReading].
 */
@SuppressLint("ClickableViewAccessibility") // taps are a shortcut; the bars offer the same moves as buttons
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun Reading(path: String, onScreen: () -> Unit, onClose: () -> Unit) {
    if (path.endsWith(".pdf", ignoreCase = true)) { PdfReading(path, onClose); return } // drawn pages, not text: its own page view
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    var bars by remember { mutableStateOf(false) }
    BackHandler { if (bars) bars = false else onClose() }
    // shortcut: opened on the main thread; it reads only the zip's index and the small package file, or the first 64 KB of other kinds
    val book = remember { try { openBook(File(path)).takeIf { it.spine.isNotEmpty() } } catch (e: IOException) { null } }
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
    var quotes by remember { mutableStateOf(shelf.quotes(path)) }
    /** Script for the place to turn to once the chapter has loaded: a search match or a highlight. */
    var locate by remember { mutableStateOf<String?>(null) }
    /** A footnote being shown: its text, and where its link leads (chapter, anchor). */
    var footnote by remember { mutableStateOf<Triple<String, Int, String?>?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(shelf.status) }
    var infoLine by remember { mutableStateOf(shelf.infoLine) }
    val voice = remember { arrayOfNulls<TextToSpeech>(1) }
    val density = ctx.resources.displayMetrics.density

    // the end of the page counts as read, so the last page of the book is 100%
    val progress = book.progress(chapter, if (pages == 0) target else (page + 1f) / pages)
    val title = book.toc.firstOrNull { it.second.substringBefore('#') == book.spine[chapter] }?.first.orEmpty()
    val numbered = stringResource(R.string.chapter_d, chapter + 1) // the name of a chapter the contents do not list
    val marked = remember(marks, chapter, page, pages) { pages > 0 && shelf.markOn(path, chapter, page.toFloat() / pages, (page + 1f) / pages) != null }
    // every save is a page turned or a chapter opened: also the moment the reading clock is read
    fun save() { if (pages > 0) { shelf.save(path, chapter, page.toFloat() / pages, book.progress(chapter, (page + 1f) / pages)); shelf.pageTurned(path) } }
    fun go(to: Int, at: Float, anchor: String? = null) {
        if (to !in book.spine.indices) return
        chapter = to; target = at; page = 0; pages = 0
        web.pending = if (anchor == null) at else null // an #anchor scrolls by itself
        web.scroll = look.scroll
        web.advance = web.width.coerceAtLeast(1).toFloat(); web.count = 1 // until the new chapter is measured
        web.css = css(look, web.width / density, web.height / density)
        web.setBackgroundColor(android.graphics.Color.parseColor(PAPER.getValue(look.theme).first))
        web.loadUrl("https://$HOST/" + book.spine[to].split('/').joinToString("/") { Uri.encode(it) } + (anchor?.let { "#$it" } ?: ""))
    }
    fun paint() = web.evaluateJavascript("mm.paint('quote'," + quotes.filter { it.chapter == chapter }.joinToString(",", "[", "]") { "[${it.start},${it.end}]" } + ")", null)
    /** Reads the page aloud; when its last words are spoken, the listener turns the page and this runs again. */
    fun speakPage() = web.evaluateJavascript("mm.spoken(${web.scrollX / density},${web.scrollY / density})") { json -> // the page is named by its place: a turn made a moment ago has not reached the script's own scroll position yet
        val pieces = (JSONTokener(json).nextValue() as? String).orEmpty().chunked(3500).ifEmpty { listOf(" ") } // an empty page still has to finish, to move on
        pieces.forEachIndexed { i, piece -> voice[0]?.speak(piece, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == pieces.lastIndex) "last" else "part") }
    }
    fun turn(forward: Boolean) {
        if (pages == 0) return // the chapter is still loading
        val next = web.page + if (forward) 1 else -1
        before = null
        if (next in 0 until web.pages) { web.show(next); page = web.page; save(); if (speaking) speakPage() } else go(chapter + if (forward) 1 else -1, if (forward) 0f else 1f)
    }
    fun stopSpeaking() { speaking = false; voice[0]?.stop() }
    fun readAloud() {
        speaking = true
        if (voice[0] != null) return speakPage()
        voice[0] = TextToSpeech(ctx) { status ->
            if (status != TextToSpeech.SUCCESS) { speaking = false; return@TextToSpeech }
            voice[0]?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                @Deprecated("the platform still requires it") override fun onError(id: String) {}
                // called on a speech thread; the page belongs to the main one
                override fun onDone(id: String) { if (id == "last") web.post { if (speaking) { if (chapter == book.spine.lastIndex && web.page >= web.pages - 1) stopSpeaking() else turn(true) } } }
            })
            speakPage()
        }
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
        web.settings.apply { blockNetworkLoads = true; allowFileAccess = false; allowContentAccess = false; textZoom = look.size; setSupportZoom(false); useWideViewPort = true; javaScriptEnabled = true } // useWideViewPort: obey the viewport line css() adds; scripts: this app's own only, see shouldInterceptRequest
        web.isHorizontalScrollBarEnabled = false; web.isVerticalScrollBarEnabled = false; web.overScrollMode = View.OVER_SCROLL_NEVER
        web.webViewClient = object : WebViewClient() {
            // runs on a WebView thread; ZipFile is safe to share
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val entry = request.url.path.orEmpty().removePrefix("/")
                // scripts are on for this app's own (run from outside the page); the header keeps every script of the book's blocked,
                // on every response: an SVG or XML file opened as a document runs its own scripts too
                val csp = mapOf("Content-Security-Policy" to "script-src 'none'; connect-src 'none'")
                // a broken entry in an untrusted book must not crash the WebView thread
                val bytes = (if (request.url.host == HOST) try { book.bytes(entry) } catch (e: Exception) { null } else null)
                    ?: return WebResourceResponse("text/plain", null, 200, "OK", csp, ByteArrayInputStream(ByteArray(0)))
                // the type the book declares: EPUB chapters are XHTML, which a browser misreads as plain HTML
                val type = book.mediaType(entry) ?: URLConnection.guessContentTypeFromName(entry) ?: "application/octet-stream"
                if ("html" !in type) return WebResourceResponse(type, null, 200, "OK", csp, ByteArrayInputStream(bytes))
                val text = bytes.decodeToString()
                val head = Regex("</head>", RegexOption.IGNORE_CASE)
                val styled = if (head.containsMatchIn(text)) text.replaceFirst(head, "${web.css}</head>") else text.replaceFirst(Regex("<body", RegexOption.IGNORE_CASE), "${web.css}<body")
                return WebResourceResponse(type, "UTF-8", 200, "OK", csp, ByteArrayInputStream(styled.toByteArray()))
            }
            // a link in the text: follow it inside the book, never out of it
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val entry = request.url.path.orEmpty().removePrefix("/")
                val i = book.spine.indexOf(entry)
                if (request.url.host == HOST && i >= 0) {
                    // a link to a short passage is a footnote: shown over the page, as e-readers do, instead of leaving it
                    val note = request.url.fragment?.let { book.note("$entry#$it") }
                    if (note != null) footnote = Triple(note, i, request.url.fragment) else jump(i, 0f, request.url.fragment)
                }
                return true
            }
            // the number of pages is known only once the chapter has been drawn
            override fun onPageFinished(view: WebView, url: String) = web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(id: Long) {
                    web.evaluateJavascript(SCRIPT, null)
                    web.evaluateJavascript(if (web.scroll) "0" else MEASURE) { w ->
                        (JSONTokener(w).nextValue() as? JSONArray)?.takeIf { it.getDouble(0) >= 1 }?.let { web.advance = it.getDouble(0).toFloat(); web.count = it.getInt(1) }
                        // the end mark has to be drawn before the last page can be scrolled to
                        web.postVisualStateCallback(0, object : WebView.VisualStateCallback() { override fun onComplete(id: Long) {
                            // the page that holds the place: rounding up instead would creep forward on every new layout
                            // an #anchor scrolls only until its target is just in view at the right edge, mid-page: its page is the next whole one
                            web.show(web.pending?.let { (it * web.pages + 0.001f).toInt() } ?: ceil(web.scrollX / web.advance - 0.01f).toInt())
                            web.pending = null
                            page = web.page; pages = web.pages; save()
                            paint()
                            locate?.let { js -> locate = null; web.evaluateJavascript(js) { at -> web.pageAt(at, density)?.let { web.show(it); page = web.page; save() } } }
                            if (speaking) speakPage()
                        } })
                    }
                }
            })
        }
        val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val zone = tapZone(e.x / web.width, e.y / web.height, shelf.taps)
                // a tap on a link is the page's to follow (see shouldOverrideUrlLoading), not a page turn
                web.evaluateJavascript("window.mm ? mm.link(${e.x / density},${e.y / density}) : false") { link ->
                    if (link != "true") when (zone) { Tap.MARK -> toggleMark(); Tap.BACK -> turn(false); Tap.FORWARD -> turn(true); Tap.MENU -> bars = !bars }
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy)) turn(vx < 0)
                return false
            }
        })
        // in pages, the page never follows the finger: drags are kept from the WebView, so it only ever rests on a whole page
        web.setOnTouchListener { _, e -> taps.onTouchEvent(e); !web.scroll && e.actionMasked == MotionEvent.ACTION_MOVE }
        // scrolling moves the place by itself
        web.setOnScrollChangeListener { _, _, _, _, _ -> if (web.scroll && web.pending == null && pages > 0 && web.page != page) { page = web.page; before = null; save() } }
        web.onHighlight = {
            web.evaluateJavascript("mm.selection()") { json ->
                (JSONTokener(json).nextValue() as? JSONArray)?.let { shelf.addQuote(path, chapter, it.getInt(0), it.getInt(1), it.getString(2)); quotes = shelf.quotes(path); paint() }
            }
        }
        // the first load waits for the view's size, which sets the page size; a new size means new page breaks
        web.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) web.post { go(chapter, if (pages == 0) target else page.toFloat() / pages) }
        }
        pageTurner = ::turn
        shelf.opened(path)
        onDispose { pageTurner = null; save(); voice[0]?.shutdown(); book.close(); web.destroy() }
    }

    val (paper, ink) = PAPER.getValue(look.theme).let { Color(android.graphics.Color.parseColor(it.first)) to Color(android.graphics.Color.parseColor(it.second)) }
    val percent = "${(progress * 100).roundToInt()}%"
    // room for the status bar even while it is hidden: a panel opening shows it, and a page that changed size then would lay itself out again
    Box(Modifier.fillMaxSize().background(paper).windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
        Column {
            AndroidView({ web }, Modifier.weight(1f).fillMaxWidth())
            // the quiet line every e-reader keeps at the foot of the page, unless switched off
            if (infoLine) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
            FilledTonalButton({ before = null; go(c, f) }, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (bars) 190.dp else 32.dp)) {
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
                        Text(title.ifEmpty { numbered }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(::toggleMark) { Icon(Icons.Filled.Star, stringResource(if (marked) R.string.remove_bookmark else R.string.add_bookmark), tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
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
                        Text(listOfNotNull(if (pages > 0 && drag == null) stringResource(R.string.page_d_of_d, page + 1, pages) else null, drag?.let { "${(it * 100).roundToInt()}%" } ?: percent, stringResource(R.string.go_to)).joinToString("  ·  "))
                    }
                    // the moves a reader makes most, in reach of the thumb and named, not only drawn
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Action(Icons.AutoMirrored.Outlined.List, R.string.contents) { sheet = "contents" }
                        Action(Icons.Filled.Search, R.string.search) { sheet = "search" }
                        Action(null, R.string.text) { sheet = "look" }
                        Action(AppIcons.Light, R.string.light) { sheet = "light" }
                        if (speaking) Action(Icons.Filled.Close, R.string.stop, ::stopSpeaking) else Action(Icons.Filled.PlayArrow, R.string.listen) { readAloud(); bars = false }
                    }
                }
            }
        }
    }

    val whole = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (sheet == "contents") ModalBottomSheet({ sheet = null }, sheetState = whole) {
        var tab by remember { mutableStateOf("contents") }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == "contents", { tab = "contents" }, { Text(stringResource(R.string.contents)) })
            FilterChip(tab == "marks", { tab = "marks" }, { Text(stringResource(R.string.bookmarks) + if (marks.isEmpty()) "" else " (${marks.size})") })
            FilterChip(tab == "quotes", { tab = "quotes" }, { Text(stringResource(R.string.highlights) + if (quotes.isEmpty()) "" else " (${quotes.size})") })
        }
        var noting by remember { mutableStateOf<Quote?>(null) }
        noting?.let { q ->
            var note by remember(q) { mutableStateOf(q.note) }
            AlertDialog({ noting = null }, title = { Text(stringResource(R.string.note)) },
                text = { OutlinedTextField(note, { note = it }, minLines = 3) },
                confirmButton = { TextButton({ shelf.setNote(path, q, note.trim()); quotes = shelf.quotes(path); noting = null }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton({ noting = null }) { Text(stringResource(android.R.string.cancel)) } })
        }
        // a book without a contents list still has its chapters
        val list = book.toc.ifEmpty { book.spine.mapIndexed { i, e -> stringResource(R.string.chapter_d, i + 1) to e } }
        LazyColumn {
            if (tab == "contents") items(list) { (label, to) ->
                val i = book.spine.indexOf(to.substringBefore('#'))
                Text(label, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = i >= 0) { jump(i, 0f, to.substringAfter('#', "").ifEmpty { null }); sheet = null; bars = false }
                    .padding(horizontal = 24.dp, vertical = 12.dp), fontWeight = if (i == chapter) FontWeight.Bold else null)
            }
            else if (tab == "quotes") {
                if (quotes.isEmpty()) item { Text(stringResource(R.string.no_highlights_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (quotes.isNotEmpty()) item {
                    val res = LocalResources.current
                    val export = exportQuotes(book.title, quotes) { q -> list.lastOrNull { (_, to) -> book.spine.indexOf(to.substringBefore('#')).let { it in 0..q.chapter } }?.first ?: res.getString(R.string.chapter_d, q.chapter + 1) }
                    TextButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, book.title).putExtra(Intent.EXTRA_TEXT, export), null)) },
                        Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.export_highlights)) }
                }
                items(quotes) { q ->
                    Row(Modifier.fillMaxWidth().clickable { locate = "mm.at(mm.range(${q.start},${q.end}))"; jump(q.chapter, 0f); sheet = null; bars = false }.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(q.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (q.note.isNotEmpty()) Text(q.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton({ noting = q }) { Icon(Icons.Filled.Edit, stringResource(R.string.note)) }
                        IconButton({ shelf.removeQuote(path, q); quotes = shelf.quotes(path); paint() }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_highlight)) }
                    }
                }
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
    if (sheet == "search") ModalBottomSheet({ sheet = null }, sheetState = whole) {
        var query by remember { mutableStateOf("") }
        // searched a moment after the typing stops, off the main thread: it reads every chapter
        val hits by produceState<List<Hit>?>(emptyList(), query) { value = null; delay(300); value = withContext(Dispatchers.IO) { book.search(query.trim()) } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp), singleLine = true, placeholder = { Text(stringResource(R.string.search_in_the_book)) })
        val found = hits
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
            if (found != null && found.isEmpty() && query.isNotBlank()) item { Text(stringResource(R.string.no_matches), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(found.orEmpty()) { hit ->
                Column(Modifier.fillMaxWidth().clickable { locate = "mm.find(${JSONObject.quote(query.trim())},${hit.nth})"; jump(hit.chapter, 0f); sheet = null; bars = false }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text(book.toc.firstOrNull { it.second.substringBefore('#') == book.spine[hit.chapter] }?.first ?: stringResource(R.string.chapter_d, hit.chapter + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("…${hit.snippet}…", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    footnote?.let { (text, to, anchor) ->
        AlertDialog({ footnote = null }, text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ footnote = null }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton({ footnote = null; jump(to, 0f, anchor) }) { Text(stringResource(R.string.go_there)) } })
    }
    if (sheet == "goto") {
        var typed by remember { mutableStateOf("") }
        val to = typed.toFloatOrNull()?.takeIf { it in 0f..100f }
        AlertDialog({ sheet = null }, title = { Text(stringResource(R.string.go_to)) },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.percent_0_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton({ val (c, f) = book.locate(to!! / 100); jump(c, f); sheet = null; bars = false }, enabled = to != null) { Text(stringResource(R.string.go)) } },
            dismissButton = { TextButton({ sheet = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    // no shade over the page: a change shows on the page above as it is made
    if (sheet == "look") ModalBottomSheet({ sheet = null }, sheetState = whole, scrimColor = Color.Transparent) {
        var tab by remember { mutableStateOf(0) }
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            PrimaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.text)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.page)) })
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) TextOptions(look, ::restyle)
                else { PageTurning(look, ::restyle); TapOptions(shelf); StatusOptions(shelf, { infoLine = it }) { status = it } }
            }
        }
    }
    if (sheet == "light") ModalBottomSheet({ sheet = null }, sheetState = whole, scrimColor = Color.Transparent) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) { Brightness(shelf, onScreen) }
    }
}

/** A button in the reading menu: an icon (or "Aa" for [icon] null) with its name under it. */
@Composable
private fun Action(icon: ImageVector?, label: Int, onClick: () -> Unit) =
    Column(Modifier.widthIn(min = 64.dp).clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (icon != null) Icon(icon, null) else Text("Aa", Modifier.height(24.dp), fontSize = 18.sp)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
