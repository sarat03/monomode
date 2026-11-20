package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake latest-release feed, shaped like GitHub's: the author has its own html_url, which must not be taken. */
class UpdateTest {
    private fun feed(tag: String = "v0.1.2", extra: String = "") = """
        {"url":"https://api.github.com/repos/sarat03/monomode/releases/1","html_url":"https://github.com/sarat03/monomode/releases/tag/$tag",
         "author":{"login":"sarat03","html_url":"https://github.com/sarat03"},"tag_name":"$tag","draft":false,"prerelease":false,
         "assets":[{"name":"a \"b\".apk","tag_name":"nested"}]$extra}
    """.trimIndent()

    @Test fun `reads the tag and the release page from the top level only`() {
        assertEquals(Release("v0.1.2", "https://github.com/sarat03/monomode/releases/tag/v0.1.2"), parseRelease(feed()))
    }

    @Test fun `a draft or a prerelease is ignored`() {
        assertNull(parseRelease(feed().replace("\"draft\":false", "\"draft\":true")))
        assertNull(parseRelease(feed().replace("\"prerelease\":false", "\"prerelease\":true")))
    }

    @Test fun `malformed feeds give null, not a crash`() {
        for (bad in listOf("", "{", "[]", "null", "not json", "{\"tag_name\":\"v1\"}", "{\"tag_name\":\"v1\",\"html_url\":", "{\"tag_name\":\"v1\",\"html_url\":\"https://x", "{\"a\":[{\"b\":"))
            assertNull(parseRelease(bad), bad)
    }

    @Test fun `newer means a higher number, a leading v is ignored`() {
        fun newer(cur: String, tag: String) = isNewer(cur, Release(tag, ""))
        assertTrue(newer("0.1.0", "v0.1.2"))
        assertTrue(newer("0.1.9", "0.2"))
        assertTrue(newer("0.9.0", "v0.10.0"))
        assertTrue(newer("0.1", "0.1.1"))
        assertFalse(newer("0.1.2", "v0.1.2"))
        assertFalse(newer("0.1.2", "v0.1.2.0"))
        assertFalse(newer("0.2.0", "v0.1.9"))
    }

    @Test fun `a malformed version is never newer`() {
        for (tag in listOf("", "v", "latest", "v1..2", "v1.x", "v1.2-beta", "99999999999999999999"))
            assertFalse(isNewer("0.1.0", Release(tag, "")), tag)
        assertFalse(isNewer("", Release("v1.0.0", "")))
        assertFalse(isNewer("dev", Release("v1.0.0", "")))
    }

    @Test fun `only a releases page of this repo may be opened`() {
        val repo = "sarat03/monomode"
        assertTrue(Release("v1", "https://github.com/sarat03/monomode/releases/tag/v1").opensIn(repo))
        for (u in listOf("http://github.com/sarat03/monomode/releases/tag/v1", "https://github.com/sarat03/monomode", "https://evil.com/sarat03/monomode/releases/x",
            "https://github.com.evil.com/sarat03/monomode/releases/x", "https://github.com/sarat03/monomode/releases/../../x", "https://github.com/sarat03/monomode-evil/releases/x", ""))
            assertFalse(Release("v1", u).opensIn(repo), u)
    }
}
