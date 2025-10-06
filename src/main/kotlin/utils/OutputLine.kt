package utils

/**
 * One logical output line from the process.
 * - text: line content (without trailing '\n')
 * - isError: true if from STDERR, else STDOUT
 */
data class OutputLine(
    val text: String,
    val isError: Boolean = false
)