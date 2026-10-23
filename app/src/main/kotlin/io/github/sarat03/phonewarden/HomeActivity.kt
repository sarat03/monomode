package io.github.sarat03.phonewarden

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import phonewarden.engine.Profile

/** The home screen while a mode with "Own home screen" is on: only the apps picked for that mode. */
class HomeActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var mode by mutableStateOf<Profile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onResume() {
        super.onResume()
        mode = Profile.entries.firstOrNull { Records.mode(this, it).exists() }
        val picked = mode?.let { homeApps(this, it) }.orEmpty()
        apps = launcherApps(this).filter { it.first in picked }
    }

    @Composable
    private fun Screen() = Column(
        Modifier.safeDrawingPadding().padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(mode?.let { "${it.label} mode" } ?: "PhoneWarden", style = MaterialTheme.typography.headlineMedium)
        for ((pkg, label) in apps) Button({ packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity) }, Modifier.fillMaxWidth()) { Text(label) }
        // always reachable, so the mode can be turned off and the old home screen comes back
        OutlinedButton({ startActivity(Intent(this@HomeActivity, MainActivity::class.java)) }, Modifier.fillMaxWidth()) { Text("PhoneWarden") }
    }
}

val Profile.label get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }

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
