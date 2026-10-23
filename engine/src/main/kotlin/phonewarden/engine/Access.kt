package phonewarden.engine

data class AccessReport(
    /** Apps that can read and control the screen. */
    val accessibility: List<String>,
    /** Apps that can read every notification. */
    val notificationAccess: List<String>,
    /** Third-party apps with their installer, when it is not the Play Store (null: sideloaded or unknown). */
    val outsidePlayStore: List<Pair<String, String?>>,
    /** Apps built into the system image. Their special access is how Android works, so it is not reported. */
    val system: Set<String>,
)

private const val PLAY_STORE = "com.android.vending"

/** `pkg/cls:pkg/cls` component lists from Settings.Secure; "null" when unset. */
private fun Shell.componentPackages(key: String): List<String> {
    val v = run(listOf("settings", "get", "secure", key)).trim()
    if (v.isEmpty() || v == "null") return emptyList()
    return v.split(":").map { it.substringBefore("/") }.filter { PKG_RE.matches(it) }.distinct()
}

fun access(sh: Shell): AccessReport {
    // shortcut: trusts the system image; a vendor app with bad intent would be hidden too. Revisit with per-brand lists.
    val system = sh.run(listOf("pm", "list", "packages", "-s")).lines().filter { it.startsWith("package:") }.map { it.drop(8).trim() }.toSet()
    val installers = sh.run(listOf("pm", "list", "packages", "-3", "-i")).lines().mapNotNull { line ->
        if (!line.startsWith("package:")) return@mapNotNull null
        val pkg = line.drop(8).substringBefore(" ")
        val installer = line.substringAfter("installer=", "null").trim().takeUnless { it == "null" }
        if (installer == PLAY_STORE) null else pkg to installer
    }
    return AccessReport(
        sh.componentPackages("enabled_accessibility_services") - system,
        sh.componentPackages("enabled_notification_listeners") - system,
        installers,
        system,
    )
}
