package io.github.sarat03.monomode

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import monomode.engine.Shell

/**
 * Answers `pm list packages [-d|-e|-s]` from PackageManager, so the App cleaner can list apps with no Wireless debugging.
 * Anything else is refused: changing apps still goes over Wireless debugging.
 */
class AppPackages(private val ctx: Context) : Shell {
    override fun run(args: List<String>): String {
        require(args.take(3) == listOf("pm", "list", "packages") && args.size <= 4) { "needs Wireless debugging: $args" }
        val keep: (ApplicationInfo) -> Boolean = when (args.getOrNull(3)) {
            null -> { _ -> true }
            "-d" -> { a -> !a.enabled }
            "-e" -> { a -> a.enabled }
            "-s" -> { a -> a.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            else -> throw IllegalArgumentException("needs Wireless debugging: $args")
        }
        return ctx.packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).filter(keep).joinToString("\n") { "package:${it.packageName}" }
    }
}
