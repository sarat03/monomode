package monomode.engine

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

internal fun unpin(sh: Shell) {
    sh.run(listOf("am", "task", "lock", "stop"))
}
