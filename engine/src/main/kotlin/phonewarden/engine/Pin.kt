package phonewarden.engine

private val COMPONENT = Regex("^\\s*([A-Za-z][\\w.]*)/([\\w.]+)$")

/** Components (`pkg/cls`) of the apps on the home screen. */
fun launchableApps(sh: Shell): List<String> =
    sh.run(listOf("cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"))
        .lines().mapNotNull { COMPONENT.find(it)?.value?.trim() }.distinctBy { it.substringBefore("/") }

/**
 * Starts [component] and pins its task (screen pinning, as from Recents). The user unpins with
 * the system gesture; [unpin] does it over adb.
 */
fun pin(sh: Shell, component: String) {
    sh.run(listOf("am", "start", "-W", "-n", component))
    val pkg = component.substringBefore("/")
    val task = sh.run(listOf("am", "stack", "list")).lines()
        .firstOrNull { it.trim().startsWith("taskId=") && " $pkg/" in it }
        ?.trim()?.removePrefix("taskId=")?.substringBefore(":")
        ?: throw IllegalStateException("$pkg did not open, so it cannot be pinned")
    sh.run(listOf("am", "task", "lock", task))
}

fun unpin(sh: Shell) {
    sh.run(listOf("am", "task", "lock", "stop"))
}
