package application

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import runner.RunInstance
import runner.ScriptRunner
import script.sampleKotlinScript
import utils.OutputLine
import utils.RunStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Application() {
    val outputLines = remember { mutableStateListOf<OutputLine>() }
    var runStatus by remember { mutableStateOf(RunStatus.Idle) }
    var lastExitCode by remember { mutableStateOf<Int?>(null) }
    var selectedLanguage by remember { mutableStateOf("Kotlin (.kts)") } // placeholder for future Swift support

    // Runner handle/job for cancellation
    var currentHandle by remember { mutableStateOf<RunInstance?>(null) }

    // Scope for launching UI-bound coroutines
    val scope = rememberCoroutineScope()
    var editorValue by remember { mutableStateOf(TextFieldValue(sampleKotlinScript())) }

    // Elapsed time
    var runStartMs by remember { mutableStateOf<Long?>(null) }
    var nowTick by remember { mutableStateOf(0L) }
    var lastRunDurationMs by remember { mutableStateOf<Long?>(null) }
    var bringEditorLineIntoView by remember { mutableStateOf<(Int, Int) -> Unit>({ _, _ -> }) }

    LaunchedEffect(runStatus) {
        if (runStatus == RunStatus.Running) {
            runStartMs = System.currentTimeMillis()

            while (runStatus == RunStatus.Running) {
                nowTick = System.currentTimeMillis()
                kotlinx.coroutines.delay(500)
            }
        }
    }


    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KotlinRunnerX") },
                    actions = {
                        LanguageSelector(
                            selected = selectedLanguage,
                            onSelect = {}
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusChip(runStatus = runStatus, lastExitCode = lastExitCode)
                        Spacer(Modifier.width(12.dp))

                        Spacer(Modifier.width(8.dp))
                        TimerChip(
                            isRunning = runStatus == RunStatus.Running,
                            runStartMs = runStartMs,
                            nowTick = nowTick,
                            lastRunDurationMs = lastRunDurationMs
                        )
                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = {
                                // Only Kotlin mode for now
                                if (runStatus == RunStatus.Running) return@Button

                                // Prepare UI state
                                outputLines.clear()
                                runStatus = RunStatus.Running

                                // Start process
                                val handle = ScriptRunner.startKotlin(
                                    scope = scope,
                                    scriptText = editorValue.text,
                                    onStdout = { line ->
                                        outputLines.add(OutputLine(line, isError = false))
                                    },
                                    onStderr = { line ->
                                        outputLines.add(OutputLine(line, isError = true))
                                    },
                                    onExit = { code ->
                                        lastExitCode = code
                                        runStatus = if (code == 0) RunStatus.FinishedSuccess else RunStatus.FinishedError
                                        currentHandle = null
                                    }
                                )
                                currentHandle = handle
                                if (handle == null) {
                                    // Failed to start 'kotlinc'
                                    runStatus = RunStatus.FinishedError
                                }
                            },
                            enabled = runStatus != RunStatus.Running,
                        ) {
                            Text("Run")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                // Cancel current process (if any)
                                val h = currentHandle

                                if (h != null) {
                                    try {
                                        h.job.cancel() // cancel streaming + waiter
                                    } catch (_: Exception) {}
                                    try {
                                        h.process.destroy()
                                    } catch (_: Exception) {}
                                    try {
                                        h.process.destroyForcibly()
                                    } catch (_: Exception) {}

                                    runStatus = RunStatus.FinishedError
                                    outputLines.add(OutputLine("[Process terminated by user]", isError = true))
                                }
                            },
                            enabled = runStatus == RunStatus.Running
                        ) {
                            Text("Stop")
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                )
            }
        ) { inner ->
            Row(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
            ) {
                // Left: Editor pane
                Column(
                    Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    Text("Editor", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    EditorPane(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                        },
                        onRegisterBringIntoView = { setBringIntoView  ->
                            bringEditorLineIntoView = setBringIntoView
                        }
                    )
                }

                // Right: Output pane
                Column(
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    Text("Output", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutputPane(
                        lines = outputLines,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateTo = { line, col ->
                            val off = computeCharOffset(editorValue.text, line, col)
                            editorValue = editorValue.copy(selection = TextRange(off, off))

                            // scroll in the editor until we reach the problematic line
                            bringEditorLineIntoView(line, col)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Kotlin (.kts)") },
                onClick = { onSelect("Kotlin (.kts)"); expanded = false }
            )
            // Swift mode can be added later (macOS only)
        }
    }
}

@Composable
fun StatusChip(runStatus: RunStatus, lastExitCode: Int?) {
    val (label, color) = when (runStatus) {
        RunStatus.Idle -> "Idle" to MaterialTheme.colorScheme.surfaceVariant
        RunStatus.Running -> "Running…" to MaterialTheme.colorScheme.primary
        RunStatus.FinishedSuccess -> "Exit 0" to MaterialTheme.colorScheme.tertiary
        RunStatus.FinishedError -> "Exit ${lastExitCode ?: "≠0"}" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun TimerChip(
    isRunning: Boolean,
    runStartMs: Long?,
    nowTick: Long,
    lastRunDurationMs: Long?
) {
    val elapsedMs = when {
        isRunning && runStartMs != null -> nowTick - runStartMs
        !isRunning && lastRunDurationMs != null -> lastRunDurationMs
        else -> null
    }

    if (elapsedMs != null && elapsedMs >= 0) {
        val label = formatDuration(elapsedMs)
        val color = if (isRunning) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surfaceVariant
        Surface(
            color = color,
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}