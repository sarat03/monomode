package io.github.sarat03.monomode

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sarat03.monomode.MainActivity.Companion.phone

@Composable
internal fun MainActivity.PairScreen() = Column(Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    AppHeader()
    Text(getString(R.string.set_up_once), style = MaterialTheme.typography.headlineSmall)
    Text(getString(R.string.pair_once_over_wireless_debugging), color = MaterialTheme.colorScheme.onSurfaceVariant)
    val dev = devOptionsOn()
    // the first step only while Developer options are hidden; the button then opens About phone for it
    Steps(*listOfNotNull(
        if (dev) null else getString(R.string.in_about_phone_tap_build),
        getString(R.string.in_developer_options_turn_on),
        getString(R.string.tap_pair_device_with_pairing),
        getString(R.string.type_the_code_into_the),
    ).toTypedArray())
    Button({
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        Pairing.prompt(this@PairScreen)
        openDeveloperOptions()
    }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (dev) getString(R.string.start_pairing) else getString(R.string.open_about_phone)) }
    BackHandler(onBack = ph::cancelSetup)
    TextButton(ph::cancelSetup, Modifier.fillMaxWidth()) { Text(getString(R.string.not_now)) }
}

@Composable
internal fun MainActivity.Steps(vararg steps: String) = Panel(Modifier.fillMaxWidth()) {
    steps.forEachIndexed { i, t ->
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
            Text(t, style = MaterialTheme.typography.bodyLarge)
        }
        if (i < steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
