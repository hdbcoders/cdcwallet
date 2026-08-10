package com.cdcwallet.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the i18n promise: every locale resource file must have exactly the
 * same keys (strings + plurals) as the English baseline, and each key must
 * use the same positional format placeholders (e.g. %1$d, %2$s). A missing
 * translation would otherwise silently fall back to English, and a missing
 * placeholder would crash format resolution at runtime.
 */
class StringResourceParityTest {

    private val base = File("src/main/res/values/strings.xml")
    private val localeDirs = listOf("values-zh-rCN", "values-ms", "values-ta")

    private fun parse(file: File): Pair<Set<String>, Map<String, Set<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val keys = mutableSetOf<String>()
        val specifiers = mutableMapOf<String, Set<String>>()
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeName == "string" || node.nodeName == "plurals") {
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                keys.add(name)
                // textContent covers plurals' <item> children as well.
                specifiers[name] = PLACEHOLDER.findAll(node.textContent).map { it.value }.toSet()
            }
        }
        return keys to specifiers
    }

    @Test
    fun everyLocaleHasExactlyTheBaseKeysAndPlaceholders() {
        assertTrue("base strings.xml not found at ${base.absolutePath}", base.exists())
        val (baseKeys, baseSpecifiers) = parse(base)

        for (dir in localeDirs) {
            val file = File("src/main/res/$dir/strings.xml")
            assertTrue("$dir/strings.xml not found at ${file.absolutePath}", file.exists())
            val (keys, specifiers) = parse(file)

            assertEquals("key set mismatch in $dir", baseKeys, keys)
            for (key in baseKeys) {
                assertEquals(
                    "placeholder mismatch in $dir for '$key'",
                    baseSpecifiers.getValue(key),
                    specifiers.getValue(key),
                )
            }
        }
    }

    private companion object {
        val PLACEHOLDER = Regex("""%\d+\$[a-z]""")
    }
}
