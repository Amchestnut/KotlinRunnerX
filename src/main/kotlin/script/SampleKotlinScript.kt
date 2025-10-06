package script

fun sampleKotlinScript(): String = """
// Kotlin .kts demo: classes, loops, when, repeated method calls, and an intentional crash.

// 1) Simple class + methods
class Greeter(private val name: String) {
    fun greet(): String =
        if (name.isNotBlank()) "Hello, ${'$'}name!" else "Hello, JetBrains team!"

    fun greetEmphatic(): String = 
        "${'$'}{greet()}!"
}

// 2) Some object example
object App {
    fun run() {
        val greeter = Greeter("KotlinRunnerX")

        // Call methods multiple times to verify everything is wired correctly
        println(greeter.greet())     // 1st call
        println(greeter.greetEmphatic())   // 2nd call
        println(greeter.greet())     // 3rd call

        val nums = (1..5).toList()
        for (i in nums) {
            println("for i=${'$'}i")
        }

        var j = 0
        while (j < 3) {
            println("while j=${'$'}j")
            j++
        }

        val y = kotlin.random.Random.nextInt(0, 3)
        when (y) {
            0 -> println("when: zero")
            1 -> println("when: one")
            else -> println("when: other")
        }

        // Small pause to simulate some work
        Thread.sleep(200)
    }
}

// 3) Top-level script execution (.kts)
println("=== Kotlin .kts sample start ===")

App.run()
println("Calling App.run() again to verify repeatability...")
App.run()

println("Before the crash...")

// (Optional) Compile-time error — uncomment to test:
// val bad: String = 123

// 4) Intentional RUNTIME error — should produce a stacktrace to stderr
println("Intentionally causing a runtime error (1/0) to test stderr...")
val crash = 1 / 0    // <-- ArithmeticException here

// 5) If it continues (no errors were found), print a few more lines
for (i in 1..7) {
    println("Line ${'$'}i")
    Thread.sleep(100)
}

println("=== Kotlin .kts sample end ===")
""".trimIndent()
