package kotlin_syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Lightweight keyword highlighter for Kotlin .kts.
 * It highlights a small set of keywords using regex.
 *
 * This is intentionally simple: it doesn't try to avoid strings/comments.
 */
class KotlinSyntaxHighlighter(
) : VisualTransformation {

    // We can choose any frequently used Kotlin keywords
    private val keywords = listOf(
        "fun", "class", "object", "val", "var",
        "if", "else", "when", "for", "while", "return", "import", "package"
    )

    private val pattern = Regex(
        keywords.joinToString(
            separator = "|",
            prefix   = "(^|[^A-Za-z0-9_])(",
            postfix  = ")(?=\$|[^A-Za-z0-9_])"
        ) { Regex.escape(it) },
        setOf(RegexOption.MULTILINE)
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        val color = SpanStyle(color = Color(0xFFFFA500))

        pattern.findAll(text.text).forEach { m ->
            val kwStart = m.groups[2]?.range?.first ?: return@forEach
            val kwEndInclusive = m.groups[2]?.range?.last ?: return@forEach
            builder.addStyle(
                style = color,
                start = kwStart,
                end = kwEndInclusive + 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

}
