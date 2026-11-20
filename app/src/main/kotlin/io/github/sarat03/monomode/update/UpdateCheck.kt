package io.github.sarat03.monomode.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import monomode.engine.Release
import monomode.engine.isNewer
import monomode.engine.parseRelease
import monomode.engine.releaseOf

/**
 * The only internet connection in MonoMode (ADR 0010): one GET of GitHub's latest-release page for this repo, opt-in and off
 * by default. GitHub sees the phone's IP address and that MonoMode asked; no account, identifier or cookie is sent, and nothing
 * is downloaded or installed.
 */
object UpdateCheck {
    const val REPO = "sarat03/monomode"
    private const val FEED = "https://api.github.com/repos/$REPO/releases/latest"
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val MAX_BYTES = 64 * 1024

    /** What the last check found; [Result.NONE] until one has run. */
    enum class Result { NONE, UP_TO_DATE, AVAILABLE, FAILED }

    var enabled by mutableStateOf(false); private set
    var last by mutableStateOf(0L); private set
    var result by mutableStateOf(Result.NONE); private set
    var release by mutableStateOf<Release?>(null); private set
    var checking by mutableStateOf(false); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)

    private fun current(app: Context) = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()

    /** Reads the settings, and an update an earlier check found (rebuilt from its tag, validated again) so it survives process death. */
    fun load(ctx: Context) {
        enabled = prefs(ctx).getBoolean("enabled", false)
        last = prefs(ctx).getLong("last", 0L)
        val found = prefs(ctx).getString("tag", null)?.let(::releaseOf)
        if (found != null && isNewer(current(ctx.applicationContext), found)) { release = found; result = Result.AVAILABLE }
    }

    fun enable(ctx: Context, on: Boolean) { enabled = on; prefs(ctx).edit().putBoolean("enabled", on).apply() }

    /** The automatic check: at most once a day (counted from the last attempt, failed or not), only when the user opted in and no mode is on. */
    fun auto(ctx: Context, modeOn: Boolean) {
        if (enabled && !modeOn && System.currentTimeMillis() - last > DAY) check(ctx)
    }

    /** One check on a background thread; "Check now" and [auto] both come here. [last] is the attempt time, so a failure does not retry at once. */
    fun check(ctx: Context) {
        if (checking) return
        checking = true
        val app = ctx.applicationContext
        last = System.currentTimeMillis()
        prefs(app).edit().putLong("last", last).apply()
        val current = current(app)
        thread {
            val found = runCatching { fetch()?.let(::parseRelease) }.getOrNull()
            val newer = found != null && isNewer(current, found)
            prefs(app).edit().apply { if (newer) putString("tag", found?.tag) else remove("tag") }.apply()
            // release before result, so the page never shows the new result with the old tag
            release = if (newer) found else null
            // an own version that does not parse cannot be compared: that is a failed check, not "up to date"
            result = when { found == null || releaseOf(current) == null -> Result.FAILED; newer -> Result.AVAILABLE; else -> Result.UP_TO_DATE }
            checking = false
        }
    }

    /** The feed body, or null on anything but a plain 200 (redirects are not followed, so the host never changes). */
    // shortcut: timeouts are per read, not a whole-request deadline, so a slow trickle can hold the thread; add a deadline if that matters.
    private fun fetch(): String? {
        val c = URL(FEED).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MonoMode")
            if (c.responseCode != 200) return null
            val buf = ByteArray(MAX_BYTES + 1)
            var n = 0
            c.inputStream.use { while (n <= MAX_BYTES) { val r = it.read(buf, n, buf.size - n); if (r < 0) break; n += r } }
            return if (n > MAX_BYTES) null else String(buf, 0, n, Charsets.UTF_8)
        } finally { c.disconnect() }
    }
}
