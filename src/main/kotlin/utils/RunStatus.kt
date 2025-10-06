package utils

/**
 * High-level execution state of a single run.
 * Idle -> Running -> FinishedSuccess || FinishedError
 * Can start a new run from any Finished state.
 */
enum class RunStatus {
    Idle, Running, FinishedSuccess, FinishedError
}