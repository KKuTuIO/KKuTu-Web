package me.kkutuio.kkutuweb.servermanagement

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean)

fun interface RemoteCommandRunner {
    fun run(command: List<String>, timeoutSeconds: Long): CommandResult
}

@Component
class ProcessRemoteCommandRunner : RemoteCommandRunner {
    override fun run(command: List<String>, timeoutSeconds: Long): CommandResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread.ofVirtual().name("server-management-output").start {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < MAX_OUTPUT_CHARS) {
                        val remaining = MAX_OUTPUT_CHARS - output.length
                        output.append(line.take(remaining)).append('\n')
                    }
                }
            }
        }
        val completed = try {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw error
        }
        if (!completed) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
        reader.join(5_000)
        return CommandResult(
            exitCode = if (completed) process.exitValue() else -1,
            output = output.toString().trimEnd(),
            timedOut = !completed
        )
    }

    private companion object {
        const val MAX_OUTPUT_CHARS = 64 * 1024
    }
}
