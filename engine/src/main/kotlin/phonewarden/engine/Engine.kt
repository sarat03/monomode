package phonewarden.engine

import java.io.File

/**
 * Port of core.py: plan, apply, restore, drift.
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

class HealthException(msg: String) : RuntimeException(msg)

val PKG_RE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

val PROTECTED: Set<String> = Shell::class.java.getResource("/protected.txt")!!.readText()
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

fun plan(sh: Shell, entries: List<Entry>, maxRisk: Risk = Risk.SAFE, allowProtected: Set<String> = emptySet()): Plan {
    val installed = sh.pkgs()
    val disabled = sh.pkgs("-d")
    val todo = mutableListOf<String>(); val already = mutableListOf<String>(); val missing = mutableListOf<String>()
    val refused = mutableListOf<String>(); val tooRisky = mutableListOf<String>()
    for ((pkg, risk) in entries) when {
        pkg in PROTECTED && pkg !in allowProtected -> refused += pkg
        pkg !in installed -> missing += pkg
        pkg in disabled -> already += pkg
        risk > maxRisk -> tooRisky += pkg
        else -> todo += pkg
    }
    return Plan(todo, already, missing, refused, tooRisky)
}

/** Home, dialer and keyboard all still resolve. */
fun healthy(sh: Shell): Boolean {
    val home = sh.run(listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"))
    val dial = sh.run(listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.DIAL"))
    val ime = sh.run(listOf("settings", "get", "secure", "default_input_method")).trim()
    return "/" in home && "/" in dial && ime !in setOf("", "null")
}

/** Record: one disabled package per line, sorted. */
fun readRecord(record: File): List<String> =
    if (record.exists()) record.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()

private fun writeRecord(record: File, pkgs: Collection<String>) {
    record.parentFile?.mkdirs()
    val tmp = File(record.path + ".tmp")
    tmp.writeText(pkgs.sorted().joinToString("") { "$it\n" })
    check(tmp.renameTo(record)) { "could not write $record" }
}

/**
 * Disables in small batches; after each, reads state back and checks health.
 * A batch that breaks Home/dialer/keyboard is re-enabled and apply stops.
 */
fun apply(sh: Shell, p: Plan, record: File, dryRun: Boolean = true, batch: Int = 5): Plan {
    if (dryRun) return p
    val done = readRecord(record).toSortedSet()
    for (chunk in p.todo.chunked(batch)) {
        for (pkg in chunk) sh.run(listOf("pm", "disable-user", "--user", "0", pkg))
        if (!healthy(sh) || !sh.pkgs("-d").containsAll(chunk)) {
            for (pkg in chunk) sh.run(listOf("pm", "enable", pkg))
            throw HealthException("batch $chunk failed verification; rolled back")
        }
        done += chunk
        writeRecord(record, done) // after every good batch so a crash never loses the record
    }
    return p
}

fun restore(sh: Shell, record: File) {
    for (pkg in readRecord(record)) if (PKG_RE.matches(pkg)) sh.run(listOf("pm", "enable", pkg))
}

/** Packages we disabled that are enabled again (usually after an OTA). */
fun drift(sh: Shell, record: File): List<String> {
    val installed = sh.pkgs()
    val disabled = sh.pkgs("-d")
    return readRecord(record).filter { it in installed && it !in disabled }
}
