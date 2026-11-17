package org.coloradomesh.meshcore.tak

import org.junit.Assert.assertEquals
import org.junit.Test

class XmlTest {
    @Test
    fun whatAReferenceProducesIsNotDecodedAgain() {
        // &#38; is "&", so the text is "&lt;", not "<".
        assertEquals("&lt;", unescapeXml("&#38;lt;"))
        assertEquals("&amp;", unescapeXml("&amp;amp;"))
        assertEquals("<", unescapeXml("&#x3c;"))
    }

    @Test
    fun decodesNamedAndNumericReferences() {
        assertEquals("<a & \"b\" 'c'>", unescapeXml("&lt;a &amp; &quot;b&quot; &apos;c&apos;&gt;"))
        assertEquals("A\u2603", unescapeXml("&#65;&#x2603;"))
        assertEquals("x&nbsp;y", unescapeXml("x&nbsp;y"))
        assertEquals("ab", unescapeXml("a&#0;b"))
    }

    @Test
    fun escapingDropsCharactersXmlForbids() {
        val text = "a\u0000b\u0001c\u001Fd\te\nf\rg\uFFFEh\uFFFFi\uD800j\uDC00k"
        assertEquals("abcd\te\nf\rghijk", escapeXml(text))
        assertEquals("map \uD83D\uDDFA ok", escapeXml("map \uD83D\uDDFA ok"))
        assertEquals("&lt;\u00e9&gt;", escapeXml("<\u00e9>"))
    }
}
