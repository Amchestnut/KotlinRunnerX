package application

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin_syntax.KotlinSyntaxHighlighter


@Composable
fun EditorPane(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    onRegisterBringIntoView: (((Int, Int) -> Unit) -> Unit)? = null
) {
    /**
     * val scope: For the scroll
     * val scrollState: Only 1 scroll state, that we share with gutter and the editor
     * var layoutResult: Keeps the last TextLayoutResult from the BasicTextField so we know where on the screen (Y koord) every line starts.
     * val lineCount: The real number of lines, without the wrap!
     */

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutterBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val gutterTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    // We register the callback to parent, scroll the editor to position (line, col)
    LaunchedEffect(onRegisterBringIntoView) {
        onRegisterBringIntoView?.let { register ->
            register { line, col ->
                val lr = layoutResult ?: return@register

                // Find the offset from (line, col)
                val charOffset = computeCharOffset(value.text, line, col)
                val lineIndex = lr.getLineForOffset(charOffset).coerceIn(0, lr.lineCount - 1)

                // y-koord of that line, and scroll
                val targetY = lr.getLineTop(lineIndex)
                scope.launch {
                    scrollState.animateScrollTo(targetY.toInt().coerceAtLeast(0))
                }
            }
        }
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF000000)
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {

                // GUTTER
                Column(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .background(gutterBackgroundColor)
                        .verticalScroll(scrollState)
                        .padding(vertical = 10.dp)
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = i.toString(),
                            color = gutterTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }

                // EDITOR
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(10.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle.Default.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState), // same scroll
                        visualTransformation = KotlinSyntaxHighlighter(),
                        onTextLayout = { layout ->
                            layoutResult = layout
                        },

                    )
                }
            }

            CompositionLocalProvider(
                LocalScrollbarStyle provides ScrollbarStyle(
                    minimalHeight = 24.dp,             // so it can not become really tiny
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            ) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                        .zIndex(1f),      // over the content
                    adapter = rememberScrollbarAdapter(scrollState),
                )
            }
        }
    }

}


// Helper: calculating offset from (line, col)
fun computeCharOffset(text: String, line1: Int, col1: Int): Int {
    val line = maxOf(1, line1)
    val col  = maxOf(1, col1)
    var curLine = 1
    var idx = 0
    val n = text.length
    while (curLine < line && idx < n) {
        if (text[idx] == '\n') curLine++
        idx++
    }
    var k = 0
    while (k < col - 1 && idx < n && text[idx] != '\n') {
        idx++; k++
    }
    if (idx > n) idx = n
    return idx
}