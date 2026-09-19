package com.ziddy.breakout.shell

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs inside the Shizuku server process with shell (ADB) privileges.
 * Spawned on demand by Shizuku via the IShellService AIDL interface.
 */
class ShellService : IShellService.Stub() {

    override fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).forEachLine {
                        stdout.appendLine(it)
                    }
                }
            }
            val errThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.errorStream)).forEachLine {
                        stderr.appendLine(it)
                    }
                }
            }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                outThread.join(2_000L)
                errThread.join(2_000L)
                return ShellProtocol.format(-1, stdout.toString(), "Command timed out")
            }
            outThread.join(2_000L)
            errThread.join(2_000L)
            ShellProtocol.format(process.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Exception) {
            ShellProtocol.format(-1, "", e.message ?: "Shell error")
        }
    }
}
