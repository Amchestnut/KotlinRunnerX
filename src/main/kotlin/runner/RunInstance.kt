package runner

import kotlinx.coroutines.Job

/**
 * Launches external script processes (Kotlin .kts for now),
 * streams stdout/stderr live, supports cancel/stop, and reports exit code.
 */
data class RunInstance(
    val job: Job,
    val process: Process
)
