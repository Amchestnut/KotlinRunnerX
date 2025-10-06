package application

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import utils.OutputLine
import kotlin.collections.forEach
import kotlin.sequences.forEach

@Composable
fun OutputPane(
    lines: List<OutputLine>,
    modifier: Modifier = Modifier,
    onNavigateTo: (line: Int, col: Int) -> Unit,
) {
    val scrollState = rememberScrollState()

    // 2 regexes for catching errors
    // 1) Compile-time errors: "script.kts:LINE:COL: error: ..."
    val compileErr = remember {
        Regex("""\bscript\.kts:(\d+):(\d+):\s+error\b""")
    }
    // 2) Runtime stacktrace errors : "(script.kts:LINE)"
    val runtimeTrace = remember {
        Regex("""\((script\.kts):(\d+)\)""")
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp)
                    .verticalScroll(scrollState)
            ) {
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No output yet. Click Run to execute your script.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                else {
                    val linkStyle = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontFamily = FontFamily.Monospace
                    )

                    lines.forEach { row ->
                        val baseColor = if (row.isError)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface

                        val text = row.text
                        val builder = AnnotatedString.Builder(text)

                        fun markClickable(range: IntRange, lineNum: Int, colNum: Int) {
                            builder.addStyle(
                                style = linkStyle,
                                start = range.first,
                                end = range.last + 1
                            )
                            builder.addStringAnnotation(
                                tag = "loc",
                                annotation = "$lineNum:$colNum",
                                start = range.first,
                                end = range.last + 1
                            )
                        }

                        // 1) Compile-time locations
                        compileErr.findAll(text).forEach { regexMatch ->
                            val ln = regexMatch.groupValues[1].toIntOrNull() ?: 1
                            val col = regexMatch.groupValues[2].toIntOrNull() ?: 1

                            // clickable part: "script.kts:LINE:COL"
                            val clickable = "script.kts:${regexMatch.groupValues[1]}:${regexMatch.groupValues[2]}"
                            val start = regexMatch.range.first + (regexMatch.value.indexOf("script.kts")).coerceAtLeast(0)
                            val end = start + clickable.length - 1

                            markClickable(start..end, ln, col)
                        }

                        // 2) Runtime locations, but we dont have the column so we treat it as 1 (script.kts:LINE)
                        runtimeTrace.findAll(text).forEach { regexMatch ->
                            val ln = regexMatch.groupValues[2].toIntOrNull() ?: 1
                            val start = regexMatch.range.first + 1
                            val end = regexMatch.range.last - 1
                            markClickable(start..end, ln, 1)
                        }

                        val annotated = builder.toAnnotatedString()
                        val hasAnnotations = annotated.getStringAnnotations("loc", 0, annotated.length).isNotEmpty()

                        if (hasAnnotations) {
                            // temporary disabling selection so the click passes through
                            DisableSelection {
                                ClickableText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = baseColor,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    onClick = { position ->
                                        annotated.getStringAnnotations("loc", position, position)
                                            .firstOrNull()
                                            ?.let { ann ->
                                                val (lnStr, colStr) = ann.item.split(":")
                                                val ln = lnStr.toIntOrNull() ?: 1
                                                val col = colStr.toIntOrNull() ?: 1
                                                onNavigateTo(ln, col)
                                            }
                                    }
                                )
                            }
                        }
                        else {
                            Text(
                                text = text,
                                color = baseColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
