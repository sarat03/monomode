package monomode.engine

import java.io.File

/**
 * Port of core.py: plan, apply, restore.
 *
 * Security model: list files and records are data, never commands. Every package name is
 * validated against PKG_RE before it reaches the shell.
 */
fun interface Shell {
    /** Runs argv on the device and returns stdout; throws if the command fails. */
    fun run(args: List<String>): String
}

private val PLAIN_ARG = Regex("^[A-Za-z0-9._/:=,@+-]+$")

/**
 * adbd hands the shell service a string that sh parses, so argv is joined by spaces and any
 * argument sh could interpret is refused. ponytail: no quoting; add it if an argument ever needs a space.
 */
fun commandLine(args: List<String>): String = args.joinToString(" ") { a ->
    require(PLAIN_ARG.matches(a)) { "argument not safe for sh: $a" }
    a
}

enum class Risk { SAFE, CAUTION, RISKY }

data class Entry(val pkg: String, val risk: Risk)

data class Plan(
    val todo: List<String> = emptyList(),
    val already: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val refused: List<String> = emptyList(),
    val tooRisky: List<String> = emptyList(),
)

internal class HealthException(msg: String) : RuntimeException(msg)

internal val PKG_RE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

internal val PROTECTED: Set<String> = Shell::class.java.getResource("/protected.txt")!!.readText()
    .lines().map { it.substringBefore("#").trim() }.filter { it.isNotEmpty() }.toSet()

/** Lines: `package[|risk]  # comment`. Risk defaults to safe. */
fun parseList(text: String): List<Entry> = text.lines().mapNotNull { raw ->
    val line = raw.substringBefore("#").trim()
    if (line.isEmpty()) return@mapNotNull null
    val pkg = line.substringBefore("|").trim()
    val risk = line.substringAfter("|", "").trim().ifEmpty { "safe" }
    require(PKG_RE.matches(pkg)) { "invalid package name: $line" }
    Entry(pkg, Risk.entries.firstOrNull { it.name.equals(risk, ignoreCase = true) } ?: throw IllegalArgumentException("unknown risk $risk for $pkg"))
}

private fun Shell.pkgs(vararg flags: String): Set<String> =
    run(listOf("pm", "list", "packages", *flags)).lines().filter { it.startsWith("package:") }.map { it.drop(8).trim() }.toSet()

fun plan(sh: Shell, entries: List<Entry>, maxRisk: Risk = Risk.SAFE): Plan {
    val installed = sh.pkgs()
    val disabled = sh.pkgs("-d")
    val todo = mutableListOf<String>(); val already = mutableListOf<String>(); val missing = mutableListOf<String>()
    val refused = mutableListOf<String>(); val tooRisky = mutableListOf<String>()
    for ((pkg, risk) in entries) when {
        pkg in PROTECTED -> refused += pkg
        pkg !in installed -> missing += pkg
        pkg in disabled -> already += pkg
        risk > maxRisk -> tooRisky += pkg
        else -> todo += pkg
    }
    return Plan(todo, already, missing, refused, tooRisky)
}

/** Home, dialer and keyboard all still resolve. */
internal fun healthy(sh: Shell): Boolean {
    val home = sh.run(listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"))
    val dial = sh.run(listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.DIAL"))
    val ime = sh.run(listOf("settings", "get", "secure", "default_input_method")).trim()
    return "/" in home && "/" in dial && ime !in setOf("", "null")
}

enum class Method(
    val apply: (String) -> List<String>,
    val undo: (String) -> List<String>,
    val tag: String,
) {
    DISABLE({ listOf("pm", "disable-user", "--user", "0", it) }, { listOf("pm", "enable", it) }, ""),

    /** For packages the vendor locks against disabling; caution level. The APK stays, so it can come back. */
    REMOVE({ listOf("pm", "uninstall", "-k", "--user", "0", it) }, { listOf("cmd", "package", "install-existing", it) }, " removed"),
}

/** What MonoMode uninstalled or disabled, and how. Record: one line per package, `pkg` (disabled) or `pkg removed`, sorted. */
internal fun recorded(record: File): Map<String, Method> =
    if (!record.exists()) emptyMap() else record.readLines().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
        line.substringBefore(" ") to if (line.endsWith(Method.REMOVE.tag)) Method.REMOVE else Method.DISABLE
    }

/** Deletes the record once nothing is left in it. */
/** Writes and flushes to disk, so the rename that follows never exposes an empty file after a power cut. */
internal fun File.writeSynced(text: String) = java.io.FileOutputStream(this).use { it.write(text.toByteArray()); it.fd.sync() }

private fun writeRecord(record: File, entries: Map<String, Method>) {
    if (entries.isEmpty()) { record.delete(); return }
    record.parentFile?.mkdirs()
    val tmp = File(record.path + ".tmp")
    tmp.writeSynced(entries.toSortedMap().entries.joinToString("") { (pkg, m) -> "$pkg${m.tag}\n" })
    check(tmp.renameTo(record)) { "could not write $record" }
}

/**
 * Disables (or removes for user 0) in small batches; after each, reads state back and checks health.
 * A batch that fails or breaks Home/dialer/keyboard is undone and apply stops.
 */
fun apply(sh: Shell, p: Plan, record: File, batch: Int = 5, method: Method = Method.DISABLE) {
    if (method == Method.REMOVE) {
        // only a built-in app's apk survives pm uninstall --user 0; a Play Store app's is deleted and cannot be restored
        val notBuiltIn = p.todo - sh.pkgs("-s")
        require(notBuiltIn.isEmpty()) { "only apps built into the phone can be removed and restored; not: ${notBuiltIn.joinToString()}" }
    }
    val done = recorded(record).toMutableMap()
    for (chunk in p.todo.chunked(batch)) {
        try {
            // recorded as soon as it is applied, so an app the undo cannot reach (connection lost) stays on the Restore list
            for (pkg in chunk) { sh.run(method.apply(pkg)); done[pkg] = method; writeRecord(record, done) }
            val ok = when (method) {
                Method.DISABLE -> sh.pkgs("-d").containsAll(chunk)
                Method.REMOVE -> sh.pkgs().none { it in chunk }
            }
            if (!healthy(sh) || !ok) throw HealthException("batch $chunk failed verification; rolled back")
        } catch (e: Exception) {
            for (pkg in chunk) if (runCatching { sh.run(method.undo(pkg)) }.isSuccess) done -= pkg // best effort: undo whatever got applied
            writeRecord(record, done)
            throw e
        }
    }
}

/** Brings back [only] (default: everything) and keeps the rest in the record; deletes the record once empty. */
fun restore(sh: Shell, record: File, only: Set<String>? = null) {
    val left = recorded(record).toMutableMap()
    for ((pkg, method) in left.toMap()) {
        if (only != null && pkg !in only) continue
        if (PKG_RE.matches(pkg)) sh.run(method.undo(pkg))
        left -= pkg
        writeRecord(record, left) // after each app, so a failure keeps the rest
    }
}

/** Every enabled app not covered by a list, so the owner can pick any of them; protected apps and [self] are left out. */
internal fun otherApps(sh: Shell, listed: Set<String>, self: String): List<String> =
    sh.pkgs("-e").filter { it !in listed && it !in PROTECTED && it != self && !it.startsWith("$self.") && !isOverlay(it) }.sorted()

// shortcut: by name; overlays only recolour or reshape the system, they are not apps. Read the apk flag if one slips through.
// com.android.internal.* are system styles too (navigation bar mode, display cutout); removing one can break navigation
private fun isOverlay(pkg: String) = "overlay" in pkg.lowercase() || pkg.startsWith("com.android.theme.") || pkg.startsWith("com.android.internal.")

/** One phone's debloat: what can go, removing in checked batches, and the restore record in [record]. */
class Debloat(val record: File) {
    /** [plan] of the curated list (any risk; the owner decides), the built-in apps, and every other app but [self]. */
    data class Scan(val plan: Plan, val system: Set<String>, val others: List<String>)

    fun scan(sh: Shell, entries: List<Entry>, self: String) =
        Scan(plan(sh, entries, maxRisk = Risk.RISKY), sh.pkgs("-s"), otherApps(sh, entries.map { it.pkg }.toSet(), self))

    /** What is uninstalled or disabled now, and how. */
    val removed: Map<String, Method> get() = recorded(record)

    fun remove(sh: Shell, pkgs: List<String>, method: Method) { apply(sh, Plan(todo = pkgs), record, method = method) }

    fun restore(sh: Shell, only: Set<String>) = restore(sh, record, only)
}
