package io.github.sarat03.monomode

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import monomode.engine.Tap
import monomode.engine.tapZone
import java.io.File
import kotlin.math.roundToInt

/** Page [index] of [pdf] drawn on white to fit [w] by [h] pixels, or null when it cannot be drawn. One page at a time: the renderer allows no more. */
private fun draw(pdf: PdfRenderer, index: Int, w: Int, h: Int): ImageBitmap? = synchronized(pdf) {
    try {
        pdf.openPage(index).use { p ->
            val scale = minOf(w.toFloat() / p.width, h.toFloat() / p.height)
            Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }.asImageBitmap()
        }
    } catch (e: Exception) { null } // closed meanwhile, or a page the phone cannot draw
}

// IOException for a broken file, SecurityException for one locked with a password
private fun open(path: String): PdfRenderer? = try { PdfRenderer(ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)).takeIf { it.pageCount > 0 } } catch (e: Exception) { null }

/** A PDF's first page as its cover. */
internal fun pdfCover(path: String): ImageBitmap? = open(path)?.use { draw(it, 0, 300, 400) }

/**
 * The reading page for a PDF: Android draws each page as a picture, one to a screen. Taps, swipes and the volume
 * keys turn pages as in [Reading]; the place is kept with the page as the "chapter".
 * shortcut: no zoom, search, highlights, bookmarks, text options or read aloud; add them when a PDF is more than a page to look at.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfReading(path: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val shelf = remember { Records.shelf(ctx) }
    var bars by remember { mutableStateOf(false) }
    BackHandler { if (bars) bars = false else onClose() }
    val pdf = remember { open(path) }
    if (pdf == null) { Text(stringResource(R.string.cant_open_book), Modifier.safeDrawingPadding().padding(16.dp)); return }

    val count = pdf.pageCount
    var page by remember { mutableStateOf((shelf.place(path)?.chapter ?: 0).coerceIn(0, count - 1)) }
    var asking by remember { mutableStateOf(false) }
    // every save is a page turned: also the moment the reading clock is read
    fun go(to: Int) { if (to in 0 until count) { page = to; shelf.save(path, to, 0f, (to + 1f) / count); shelf.pageTurned(path) } }
    DisposableEffect(pdf) {
        pageTurner = { forward -> go(page + if (forward) 1 else -1) }
        shelf.opened(path)
        onDispose { pageTurner = null; synchronized(pdf) { pdf.close() } }
    }

    val label = stringResource(R.string.page_d_of_d, page + 1, count)
    Box(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
        Column {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        when (tapZone(at.x / size.width, at.y / size.height, shelf.taps)) { Tap.FORWARD -> go(page + 1); Tap.BACK -> go(page - 1); else -> bars = !bars }
                    }
                }
                .pointerInput(Unit) {
                    var moved = 0f
                    detectHorizontalDragGestures(onDragStart = { moved = 0f }, onDragEnd = { if (moved < -80) go(page + 1) else if (moved > 80) go(page - 1) }) { _, by -> moved += by }
                }) {
                val w = constraints.maxWidth; val h = constraints.maxHeight
                // the page before stays up until the next one is drawn, so a turn does not flash
                val image by produceState<ImageBitmap?>(null, page, w, h) { withContext(Dispatchers.IO) { draw(pdf, page, w, h) }?.let { value = it } }
                image?.let { Image(it, label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            val status = remember { shelf.status }
            if (remember { shelf.infoLine }) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                Text(listOfNotNull(label.takeIf { "page" in status }, "${((page + 1f) / count * 100).roundToInt()}%".takeIf { "percent" in status }).joinToString("  ·  "), color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        if (bars) {
            val bar = MaterialTheme.colorScheme.surfaceContainerHigh
            Surface(Modifier.align(Alignment.TopStart).fillMaxWidth(), color = bar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.library)) }
                    Text(File(path).nameWithoutExtension, Modifier.weight(1f).padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = bar) {
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    var drag by remember { mutableStateOf<Float?>(null) }
                    val place = stringResource(R.string.place_in_the_book)
                    if (count > 1) Slider(drag ?: page.toFloat(), { drag = it }, Modifier.semantics { contentDescription = place }, valueRange = 0f..(count - 1f),
                        onValueChangeFinished = { drag?.let { go(it.roundToInt()) }; drag = null })
                    TextButton({ asking = true }) { Text(stringResource(R.string.page_d_of_d, (drag?.roundToInt() ?: page) + 1, count) + "  ·  " + stringResource(R.string.go_to)) }
                }
            }
        }
    }
    if (asking) {
        var typed by remember { mutableStateOf("") }
        val to = typed.toIntOrNull()?.takeIf { it in 1..count }
        AlertDialog({ asking = false }, title = { Text(stringResource(R.string.go_to)) },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.page_1_to_d, count)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton({ go(to!! - 1); asking = false; bars = false }, enabled = to != null) { Text(stringResource(R.string.go)) } },
            dismissButton = { TextButton({ asking = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
