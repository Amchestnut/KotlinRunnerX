package org.example

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import application.Application
import java.awt.Dimension


fun main() = application {
    val state = rememberWindowState(
        width = 1400.dp,
        height = 900.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinRunnerX",
        state = state,
    ) {
        // Minimum window size
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1100, 700)
        }

//        println(System.getenv("PATH"))
        Application()
    }
}