package monomode.engine

/** The newest release's tag (like "v0.1.2"). Pure; the one network call is in the app (ADR 0010). */
data class Release(val tag: String)

private val TAG = Regex("v?[0-9]+(\\.[0-9]+)*")

/** The release from GitHub's latest-release JSON, or null for a draft, a prerelease, a malformed feed or a tag that is not v?digits(.digits)*. */
fun parseRelease(json: String): Release? {
    val top = topLevel(json) ?: return null
    if (top["draft"] == "true" || top["prerelease"] == "true") return null
    return releaseOf(top["tag_name"] ?: return null)
}

/** The release for a tag, or null unless the whole tag matches v?digits(.digits)*; the page URL is only ever built from such a tag. */
fun releaseOf(tag: String): Release? = if (TAG.matches(tag)) Release(tag) else null

/** This repo's page for the release, built here; the feed's own html_url is never used. */
fun Release.page(repo: String) = "https://github.com/$repo/releases/tag/$tag"

/** True when the release's number is higher than [current]'s (versionName). Same, older or malformed is false. */
fun isNewer(current: String, release: Release): Boolean {
    val have = numbers(current) ?: return false
    val want = numbers(release.tag) ?: return false
    for (i in 0 until maxOf(have.size, want.size)) {
        val a = have.getOrElse(i) { 0 }
        val b = want.getOrElse(i) { 0 }
        if (a != b) return b > a
    }
    return false
}

private fun numbers(v: String): List<Int>? =
    v.removePrefix("v").removePrefix("V").split(".").map { p -> if (p.isNotEmpty() && p.all { it in '0'..'9' }) p.toIntOrNull() ?: return null else return null }

// shortcut: no JSON library in the engine, so a scan for the top-level string and true/false members; \u escapes stay raw. Upgrade if a feed needs more.
private fun topLevel(json: String): Map<String, String>? {
    val s = json.trim()
    if (!s.startsWith("{")) return null
    val out = HashMap<String, String>()
    var i = 0
    var depth = 0
    fun str(): String {
        val sb = StringBuilder()
        i++
        while (s[i] != '"') { if (s[i] == '\\') i++; sb.append(s[i]); i++ }
        i++
        return sb.toString()
    }
    fun skipSpace() { while (i < s.length && s[i].isWhitespace()) i++ }
    try {
        while (i < s.length) {
            when (s[i]) {
                '{', '[' -> { depth++; i++ }
                '}', ']' -> { depth--; i++ }
                '"' -> {
                    val text = str()
                    skipSpace()
                    if (depth == 1 && s[i] == ':') {
                        i++; skipSpace()
                        if (s[i] == '"') out[text] = str()
                        else if (s[i].isLetter()) { val st = i; while (s[i].isLetter()) i++; out[text] = s.substring(st, i) }
                    }
                }
                else -> i++
            }
        }
    } catch (e: IndexOutOfBoundsException) { return null }
    return if (depth == 0) out else null
}
