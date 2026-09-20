package com.d4viddf.hyperbridge.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

data class RootCommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0
}

object RootShellService {
    suspend fun execute(command: String): RootCommandResult = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val error = StringBuilder()
        val errorReader = thread(start = true, isDaemon = true, name = "hyperbridge-root-stderr") {
            process.errorStream.bufferedReader().use { error.append(it.readText()) }
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        errorReader.join()
        RootCommandResult(exit, output.trim(), error.toString().trim())
    }

    suspend fun isAvailable(): Boolean = execute("id").let {
        it.success && it.stdout.contains("uid=0")
    }

    suspend fun restartPackages(packages: Set<String>): RootCommandResult {
        val allowed = packages.intersect(
            setOf("com.android.systemui", "com.xiaomi.xmsf", "com.miui.home", "com.miui.screenrecorder")
        )
        if (allowed.isEmpty()) return RootCommandResult(0, "Nothing to restart", "")
        return execute(allowed.joinToString(" && ") {
            if (it == "com.android.systemui") "killall ${shellQuote(it)}"
            else "am force-stop ${shellQuote(it)}"
        })
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
