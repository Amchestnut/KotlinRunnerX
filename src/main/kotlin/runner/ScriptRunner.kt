package runner

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

object ScriptRunner {

    /**
     * Runs a Kotlin .kts script via "kotlinc -script".
     * Streams stdout/stderr line-by-line and exposes Process + Job through RunHandle.
     *
     * Usage:
     *   val handle = ScriptRunner.startKotlin(
     *       scope,
     *       scriptText = "...",
     *       onStdout = { println("OUT: $it") },
     *       onStderr = { println("ERR: $it") },
     *       onExit   = { code -> println("EXIT: $code") }
     *   )
     */
    fun startKotlin(
        scope: CoroutineScope,
        scriptText: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onExit: (Int) -> Unit
    ): RunInstance? {
        // Creating temp script file so errors report as "script.kts:line:col".
        val temporaryScriptFile = createTempKts(scriptText)

        // Build command
        val command = buildKotlincCommand(temporaryScriptFile.toAbsolutePath().toString())

        // Build the OS process: kotlinc -script tempFile
        val process: Process = try {
            ProcessBuilder(command)
                .directory(temporaryScriptFile.parent.toFile())   // <<< bitno: cwd = temp dir; fajl je "script.kts"
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            // kotlinc not found or cannot start process
            onStderr("Failed to start 'kotlinc': ${e.message ?: e.javaClass.simpleName}")
            onExit(-1)
            return null
        }

        // Supervisor job for multiple streaming and waiters that we have down here
        val supervisor = SupervisorJob()
        val runScope = scope + supervisor + Dispatchers.IO

        // Streaming stdout... line by line, we send the line to onStdout, that adds it on the UI
        val stdoutJob = runScope.launch {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (isActive) {
                    line = reader.readLine() ?: break
                    onStdout(line)
                }
            }
        }

        // Stream stderr
        val stderrJob = runScope.launch {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line: String?
                while (isActive) {
                    line = reader.readLine() ?: break
                    onStderr(line)
                }
            }
        }

        // Wait for the process to end (exit) and notify.
        // Need this catch block, otherwise it can throw InterruptedException
        val waiterJob = runScope.launch {
            val code = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                // If process is interrupted, ensure process is terminated and send non-zero exit
                try {
                    process.destroy()
                } catch (_: Exception) { }
                -1
            }
            onExit(code)
            // Cleanup temporary file
            try {
                Files.deleteIfExists(temporaryScriptFile)
            } catch (_: Exception) { }
        }

        // If the parent job is cancelled, kill the child process
        val findAndKillChildProcess = runScope.launch {
            supervisor.join()
            if (!processHasExited(process)) {
                killProcessTree(process)
            }
        }

        // Return a single handle. Cancelling job will cancel all children and stop the process
        val mainJob = runScope.launch {
            try {
                joinAll(stdoutJob, stderrJob, waiterJob, findAndKillChildProcess)
            } finally {
                // Ensure nothing is left
                if (!processHasExited(process)) {
                    killProcessTree(process)
                }
            }
        }

        return RunInstance(mainJob, process)
    }

    private fun killProcessTree(p: Process) {
        /**
         * We want to kill and stop any child processes, if the parent is destroyed
         */
        try {
            val h = p.toHandle()

            // First we try "nice" to destroy child processes
            h.descendants().forEach { child ->
                try { child.destroy() } catch (_: Exception) {}
            }
            try {
                p.destroy()
            } catch (_: Exception) {}

            // Give it a little pause
            try {
                Thread.sleep(120)
            } catch (_: InterruptedException) {}

            // Force stopping any remaining process
            h.descendants().forEach { child ->
                try {
                    if (child.isAlive) child.destroyForcibly()
                } catch (_: Exception) {}
            }
            try {
                if (p.isAlive) p.destroyForcibly()
            } catch (_: Exception) {}
        }
        catch (_: Exception) {}
    }

    private fun processHasExited(p: Process): Boolean =
        try {
            p.exitValue()
            true
        } catch (_: IllegalThreadStateException) {
            false
        }

    private fun createTempKts(scriptText: String): Path {
        // Creating temporary directory, and inside it -> a file with the name: "script.kts"
        val directory = Files.createTempDirectory("kotlinrunnerx-")
        val file = directory.resolve("script.kts")

        Files.writeString(file, scriptText, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        // cleanup
        try {
            File(directory.toUri()).deleteOnExit()
        } catch (_: Exception) {}
        try {
            File(file.toUri()).deleteOnExit()
        } catch (_: Exception) {}

        return file
    }


    /**
     * Here we build the desired command "kotlinc -script foo.kts"
     * Here it has been run and tested on windows, but it should work on macOS
     */
    private fun buildKotlincCommand(scriptPath: String): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val customKotlinc = System.getProperty("kotlinc.path")?.takeIf { it.isNotBlank() }

        // Base executable (prefer custom, else plain "kotlinc")
        val kotlincExec = customKotlinc ?: "kotlinc"

        // On Windows, prefer executing through cmd to run .bat reliably
        val launcher = if (isWindows) listOf("cmd", "/c", kotlincExec) else listOf(kotlincExec)

        // Resolve optional classpath (Windows only)
        val extraClasspath = if (isWindows) resolveKotlinLibClasspath(customKotlinc) else null

        val args = mutableListOf<String>()
        if (!extraClasspath.isNullOrBlank()) {
            // -cp must come before -script
            args += listOf("-cp", extraClasspath)
        }
        args += listOf("-script", scriptPath)

        return launcher + args
    }

    /**
     * Try to locate Kotlin lib folder and build a classpath string with the jars needed
     * for scripting: stdlib + scripting.* + (optionally) main-kts.
     *
     * Search order (Windows only):
     * 1) If kotlinc.path is provided, try <kotlinc.path>\..\lib
     * 2) KOTLIN_HOME\lib
     * 3) As a last resort, common install path "C:\Program Files\Kotlinc\lib"
     */
    private fun resolveKotlinLibClasspath(customKotlincPath: String?): String? {
        fun jarsIn(lib: Path): List<Path>? {
            if (!Files.isDirectory(lib)) return null
            val wantLibraries = listOf(
                "kotlin-stdlib",
                "kotlin-scripting-jvm-host",
                "kotlin-scripting-jvm",
                "kotlin-script-runtime",
                "kotlin-main-kts" // optional, but nice to have
            )
            val found = Files.list(lib).use { stream ->
                stream
                    .filter { p ->
                        val n = p.fileName.toString()
                        n.endsWith(".jar", ignoreCase = true) &&
                                wantLibraries.any { w -> n.startsWith(w, ignoreCase = true) }
                    }
                    .toList()
            }
            return if (found.isNotEmpty()) found else null
        }

        // 1) Near custom kotlinc path (..\lib from the executable)
        val nearCustomLib = customKotlincPath
            ?.let { Paths.get(it).toAbsolutePath().normalize() }
            ?.let { exe ->
                // exe could be ...\bin\kotlinc(.bat) → we want ...\lib
                val binDir = exe.parent
                if (binDir != null && binDir.fileName.toString().equals("bin", ignoreCase = true)) {
                    binDir.parent?.resolve("lib")
                } else null
            }

        val candidates = buildList<Path> {
            if (nearCustomLib != null)
                add(nearCustomLib)

            System.getenv("KOTLIN_HOME")?.let {
                add(Paths.get(it, "lib"))
            }

            add(Paths.get("C:\\Program Files\\Kotlinc\\lib"))
        }

        val libDir = candidates.firstNotNullOfOrNull { p -> jarsIn(p)?.let { p } } ?: return null
        val jars = Files.list(libDir).use { stream ->
            stream.filter { p ->
                val n = p.fileName.toString()
                n.endsWith(".jar", true) && (
                    n.startsWith("kotlin-stdlib", true) ||
                    n.startsWith("kotlin-scripting-jvm-host", true) ||
                    n.startsWith("kotlin-scripting-jvm", true) ||
                    n.startsWith("kotlin-script-runtime", true) ||
                    n.startsWith("kotlin-main-kts", true)
                )
            }.toList()
        }
        if (jars.isEmpty())
            return null

        // Windows classpath separator is ';'
        return jars.joinToString(";") { it.toAbsolutePath().toString() }
    }



}