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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elianiva.jisholens.data.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    repository: DictionaryRepository,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (element) {
        JsonNull -> Unit
        is JsonArray -> {
            if (element.any(::isBlock)) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    element.forEach { child ->
                        GlossaryContent(child, dictionaryId, repository, onQuery)
                    }
                }
            } else {
                Text(
                    text = inline(element, onQuery),
                    modifier = modifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is JsonObject -> BlockElement(element, dictionaryId, repository, onQuery, modifier)
        is JsonPrimitive -> Text(
            text = element.contentOrNull.orEmpty(),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BlockElement(
    element: JsonObject,
    dictionaryId: Long,
    repository: DictionaryRepository,
    onQuery: (String) -> Unit,
    modifier: Modifier,
) {
    val tag = tagOf(element)
    val content = element["content"]
    when (tag) {
        "ul", "ol" -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (content as? JsonArray)?.forEachIndexed { index, child ->
                    val item = child as? JsonObject
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (tag == "ol") "${index + 1}." else "\u2022",
                            modifier = Modifier.padding(end = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        GlossaryContent(
                            element = item?.get("content") ?: child,
                            dictionaryId = dictionaryId,
                            repository = repository,
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
            repository = repository,
            onQuery = onQuery,
            modifier = modifier,
        )
        "table" -> TableElement(element, dictionaryId, repository, onQuery, modifier)
        "img" -> DictionaryImage(element, dictionaryId, repository, modifier)
        "ruby" -> {
            val segments = rubySegments(element)
            RubyText(segments = segments, modifier = modifier, baseSize = 16.sp, readingSize = 9.sp)
        }
        "details" -> {
            Column(modifier) {
                (content as? JsonArray)?.forEach { child ->
                    GlossaryContent(child, dictionaryId, repository, onQuery)
                }
            }
        }
        else -> {
            if (content != null) {
                GlossaryContent(content, dictionaryId, repository, onQuery, modifier)
            }
        }
    }
}

@Composable
private fun TableElement(
    element: JsonObject,
    dictionaryId: Long,
    repository: DictionaryRepository,
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
                        repository = repository,
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
    repository: DictionaryRepository,
    modifier: Modifier,
) {
    val path = (element["path"] as? JsonPrimitive)?.contentOrNull ?: return
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dictionaryId, path) {
        value = withContext(Dispatchers.IO) {
            repository.media(dictionaryId, path)
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
                .padding(vertical = 4.dp),
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

private fun inline(element: JsonElement, onQuery: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    appendInline(element, onQuery)
}

private fun AnnotatedString.Builder.appendInline(element: JsonElement, onQuery: (String) -> Unit) {
    when (element) {
        JsonNull -> Unit
        is JsonPrimitive -> append(element.contentOrNull.orEmpty())
        is JsonArray -> element.forEachIndexed { index, child ->
            if (index > 0) append(' ')
            appendInline(child, onQuery)
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
                    appendInline(base ?: JsonNull, onQuery)
                    val readingText = reading?.let { inline(it, onQuery).text }.orEmpty()
                    if (readingText.isNotBlank()) append("（$readingText）")
                }
                "a" -> {
                    val href = (element["href"] as? JsonPrimitive)?.contentOrNull
                    val text = inline(element["content"] ?: JsonNull, onQuery)
                    if (href == null) {
                        append(text)
                    } else {
                        append(text)
                        val internal = internalQuery(href)
                        val link: LinkAnnotation = if (internal != null) {
                            LinkAnnotation.Clickable(
                                tag = href,
                                styles = linkStyles(),
                                linkInteractionListener = LinkInteractionListener { onQuery(internal) },
                            )
                        } else {
                            LinkAnnotation.Url(href, linkStyles())
                        }
                        when (link) {
                            is LinkAnnotation.Url -> addLink(link, 0, length)
                            is LinkAnnotation.Clickable -> addLink(link, 0, length)
                        }
                    }
                }
                else -> {
                    applyElementStyle(element) {
                        appendInline(element["content"] ?: JsonNull, onQuery)
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

private fun linkStyles(): TextLinkStyles = TextLinkStyles(
    style = SpanStyle(
        color = androidx.compose.ui.graphics.Color(0xFF1B6EF3),
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
