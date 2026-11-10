package com.elianiva.jisholens.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elianiva.jisholens.ui.theme.JapaneseLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Body text for definitions: large enough to read a dense gloss, and pinned to Japanese glyphs. */
@Composable
private fun glossaryStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale)

private val blockTags = setOf(
    "ul", "ol", "li", "div", "p", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
    "details", "summary", "img",
)

/**
 * Renders the small structured-content subset that dictionaries use in
 * practice. Unknown tags fall back to their content, and unknown style keys are
 * ignored rather than dropped as text.
 */
@Composable
fun GlossaryContent(
    element: JsonElement,
    dictionaryId: Long,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (element) {
        JsonNull -> Unit
        is JsonArray -> {
            if (element.any(::isBlock)) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    element.forEach { child ->
                        GlossaryContent(child, dictionaryId, loadMedia, onQuery)
                    }
                }
            } else {
                Text(
                    text = inline(element, onQuery, MaterialTheme.colorScheme.primary),
                    modifier = modifier,
                    style = glossaryStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is JsonObject -> BlockElement(element, dictionaryId, loadMedia, onQuery, modifier)
        is JsonPrimitive -> Text(
            text = element.contentOrNull.orEmpty(),
            modifier = modifier,
            style = glossaryStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BlockElement(
    element: JsonObject,
    dictionaryId: Long,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onQuery: (String) -> Unit,
    modifier: Modifier,
) {
    val tag = tagOf(element)
    val content = element["content"]
    when (tag) {
        "ul", "ol" -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (content as? JsonArray)?.forEachIndexed { index, child ->
                    val item = child as? JsonObject
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (tag == "ol") "${index + 1}." else "\u2022",
                            modifier = Modifier.padding(end = 10.dp),
                            style = glossaryStyle().copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        GlossaryContent(
                            element = item?.get("content") ?: child,
                            dictionaryId = dictionaryId,
                            loadMedia = loadMedia,
                            onQuery = onQuery,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        "li" -> GlossaryContent(
            element = content ?: JsonNull,
            dictionaryId = dictionaryId,
            loadMedia = loadMedia,
            onQuery = onQuery,
            modifier = modifier,
        )
        "table" -> TableElement(element, dictionaryId, loadMedia, onQuery, modifier)
        "img" -> DictionaryImage(element, dictionaryId, loadMedia, modifier)
        "ruby" -> {
            val segments = rubySegments(element)
            RubyText(
                segments = segments,
                modifier = modifier,
                baseStyle = glossaryStyle(),
                readingStyle = MaterialTheme.typography.labelSmall,
            )
        }
        "details" -> {
            Column(modifier) {
                (content as? JsonArray)?.forEach { child ->
                    GlossaryContent(child, dictionaryId, loadMedia, onQuery)
                }
            }
        }
        else -> {
            if (content != null) {
                GlossaryContent(content, dictionaryId, loadMedia, onQuery, modifier)
            }
        }
    }
}

@Composable
private fun TableElement(
    element: JsonObject,
    dictionaryId: Long,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onQuery: (String) -> Unit,
    modifier: Modifier,
) {
    val rows = (element["content"] as? JsonArray).orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            val cells = ((row as? JsonObject)?.get("content") as? JsonArray).orEmpty()
            Row(Modifier.fillMaxWidth()) {
                cells.forEach { cell ->
                    GlossaryContent(
                        element = (cell as? JsonObject)?.get("content") ?: cell,
                        dictionaryId = dictionaryId,
                        loadMedia = loadMedia,
                        onQuery = onQuery,
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryImage(
    element: JsonObject,
    dictionaryId: Long,
    loadMedia: suspend (Long, String) -> ByteArray?,
    modifier: Modifier,
) {
    val path = (element["path"] as? JsonPrimitive)?.contentOrNull ?: return
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dictionaryId, path) {
        value = withContext(Dispatchers.IO) {
            loadMedia(dictionaryId, path)
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.asImageBitmap()
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

private fun isBlock(element: JsonElement): Boolean = when (element) {
    is JsonArray -> element.any(::isBlock)
    is JsonObject -> tagOf(element) in blockTags ||
        (tagOf(element).isEmpty() && element["content"]?.let(::isBlock) == true)
    else -> false
}

private fun tagOf(element: JsonObject): String =
    (element["tag"] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun inline(element: JsonElement, onQuery: (String) -> Unit, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
    appendInline(element, onQuery, linkColor)
}

private fun AnnotatedString.Builder.appendInline(element: JsonElement, onQuery: (String) -> Unit, linkColor: Color) {
    when (element) {
        JsonNull -> Unit
        is JsonPrimitive -> append(element.contentOrNull.orEmpty())
        is JsonArray -> element.forEachIndexed { index, child ->
            if (index > 0) append(' ')
            appendInline(child, onQuery, linkColor)
        }
        is JsonObject -> {
            val tag = tagOf(element)
            when (tag) {
                "br" -> append('\n')
                "rt", "rp" -> Unit
                "ruby" -> {
                    val children = (element["content"] as? JsonArray).orEmpty()
                    val base = children.firstOrNull { tagOf(it as? JsonObject ?: JsonObject(emptyMap())) != "rt" }
                    val reading = children.firstOrNull {
                        tagOf(it as? JsonObject ?: JsonObject(emptyMap())) == "rt"
                    }
                    appendInline(base ?: JsonNull, onQuery, linkColor)
                    val readingText = reading?.let { inline(it, onQuery, linkColor).text }.orEmpty()
                    if (readingText.isNotBlank()) append("（$readingText）")
                }
                "a" -> {
                    val href = (element["href"] as? JsonPrimitive)?.contentOrNull
                    val text = inline(element["content"] ?: JsonNull, onQuery, linkColor)
                    if (href == null) {
                        append(text)
                    } else {
                        append(text)
                        val internal = internalQuery(href)
                        val link: LinkAnnotation = if (internal != null) {
                            LinkAnnotation.Clickable(
                                tag = href,
                                styles = linkStyles(linkColor),
                                linkInteractionListener = LinkInteractionListener { onQuery(internal) },
                            )
                        } else {
                            LinkAnnotation.Url(href, linkStyles(linkColor))
                        }
                        when (link) {
                            is LinkAnnotation.Url -> addLink(link, 0, length)
                            is LinkAnnotation.Clickable -> addLink(link, 0, length)
                        }
                    }
                }
                else -> {
                    applyElementStyle(element) {
                        appendInline(element["content"] ?: JsonNull, onQuery, linkColor)
                    }
                }
            }
        }
    }
}

private inline fun AnnotatedString.Builder.applyElementStyle(
    element: JsonObject,
    crossinline block: AnnotatedString.Builder.() -> Unit,
) {
    val style = element["style"] as? JsonObject
    if (style == null) {
        block()
        return
    }
    val span = SpanStyle(
        color = (style["color"] as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
            ?: Color.Unspecified,
        fontWeight = (style["fontWeight"] as? JsonPrimitive)?.contentOrNull
            ?.let { if (it == "bold" || it.toIntOrNull()?.let { weight -> weight >= 600 } == true) FontWeight.Bold else null },
        fontStyle = (style["fontStyle"] as? JsonPrimitive)?.contentOrNull
            ?.let { if (it == "italic") FontStyle.Italic else null },
        textDecoration = (style["textDecoration"] as? JsonPrimitive)?.contentOrNull
            ?.let { if (it.contains("underline")) TextDecoration.Underline else null },
    )
    withStyle(span) { block() }
}

private fun linkStyles(color: Color): TextLinkStyles = TextLinkStyles(
    style = SpanStyle(
        color = color,
        textDecoration = TextDecoration.Underline,
    ),
)

private fun internalQuery(href: String): String? {
    val uri = Uri.parse(href)
    return uri.getQueryParameter("query")
        ?: uri.getQueryParameter("kanji")
        ?: uri.fragment?.substringAfter("kanji=")?.takeIf { it.isNotBlank() }
}

private fun rubySegments(element: JsonObject): List<com.elianiva.jisholens.dictionary.RubySegment> {
    val children = (element["content"] as? JsonArray).orEmpty()
    val base = children.firstOrNull { tagOf(it as? JsonObject ?: JsonObject(emptyMap())) != "rt" }
    val reading = children.firstOrNull { tagOf(it as? JsonObject ?: JsonObject(emptyMap())) == "rt" }
    val baseText = plainText(base ?: JsonNull)
    val readingText = plainText(reading ?: JsonNull)
    return listOf(com.elianiva.jisholens.dictionary.RubySegment(baseText, readingText.ifBlank { null }))
}

private fun plainText(element: JsonElement): String = when (element) {
    JsonNull -> ""
    is JsonPrimitive -> element.contentOrNull.orEmpty()
    is JsonArray -> element.joinToString("") { plainText(it) }
    is JsonObject -> plainText(element["content"] ?: JsonNull)
}
