package io.github.mothlight.sample

import kotlinx.serialization.json.*
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

/** Parse HTML as inert text: never execute scripts or fetch embedded resources. */
internal fun articleText(response: String, expectedId: Long): String {
    val row = Json.parseToJsonElement(response).jsonObject
    require(row.getValue("id").jsonPrimitive.long == expectedId) { "Unexpected article ID" }
    val html = row.getValue("body_html").jsonPrimitive.content
    val text = StringBuilder()
    val blocks = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote", "ul", "ol", "table", "tr")
    var hiddenDepth = 0
    fun newline() { if (text.isNotEmpty() && !text.endsWith("\n")) text.append('\n') }
    ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
        override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
            val name = tag.toString()
            if (name in setOf("script", "style")) hiddenDepth++
            if (hiddenDepth > 0) return
            if (name in blocks) { newline(); text.append('\n') }
            if (name == "li") { newline(); text.append("• ") }
        }
        override fun handleEndTag(tag: HTML.Tag, position: Int) {
            val name = tag.toString()
            if (name in setOf("script", "style")) { hiddenDepth = (hiddenDepth - 1).coerceAtLeast(0); return }
            if (hiddenDepth > 0) return
            if (name in blocks || name == "li") newline()
            if (name == "td" || name == "th") text.append("  ")
        }
        override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
            if (hiddenDepth > 0) return
            if (tag == HTML.Tag.BR || tag == HTML.Tag.HR) newline()
            if (tag == HTML.Tag.IMG) {
                val alt = attributes.getAttribute(HTML.Attribute.ALT)?.toString().orEmpty()
                if (alt.isNotBlank()) { newline(); text.append('[').append(alt).append(']'); newline() }
            }
        }
        override fun handleText(data: CharArray, position: Int) { if (hiddenDepth == 0) text.append(data) }
    }, true)
    return text.toString().replace(Regex("\n{3,}"), "\n\n").trim().also {
        require(it.isNotBlank()) { "Article text is empty" }
    }
}
