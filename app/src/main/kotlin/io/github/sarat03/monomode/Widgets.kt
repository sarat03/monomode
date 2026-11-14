package io.github.sarat03.monomode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.query

/** The status card at the top of a page: icon, one big line, a sentence, then its actions. */
@Composable
internal fun MainActivity.Hero(tone: Tone, icon: ImageVector, title: String, text: String, actions: @Composable () -> Unit = {}) =
    Card(colors = CardDefaults.cardColors(tone.bg, tone.fg), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(32.dp))
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }

@Composable
internal fun MainActivity.neutralTone() = Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun MainActivity.Note(text: String) = errorTone().let { t ->
    Card(colors = CardDefaults.cardColors(t.bg, t.fg), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null)
            Text(text, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
            IconButton(ph::dismiss) { Icon(Icons.Outlined.Close, getString(R.string.close)) }
        }
    }
}

/** A row that opens a pop-up list: the title, what is chosen, and a chevron. */
@Composable
internal fun MainActivity.ListRow(title: String, value: String, onClick: () -> Unit) = Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Android's list pop-up: a title, a scrolling list, and Done. */
@Composable
internal fun MainActivity.ListDialog(title: String, note: String? = null, onClose: () -> Unit, search: Boolean = false, items: LazyListScope.(query: String) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text(getString(R.string.done)) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (search) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text(getString(R.string.search)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(q) }
            }
        },
    )
}

internal fun Pair<String, String>.matches(q: String) = q.isBlank() || second.contains(q, true) || first.contains(q, true)

@Composable
internal fun MainActivity.AppIcon(pkg: String) {
    val px = (32 * resources.displayMetrics.density).toInt()
    val icon = smallIcons.getOrPut(pkg) { runCatching { packageManager.getApplicationIcon(pkg) }.getOrElse { packageManager.defaultActivityIcon }.toBitmap(px) }
    androidx.compose.foundation.Image(icon, null, Modifier.size(32.dp))
}
