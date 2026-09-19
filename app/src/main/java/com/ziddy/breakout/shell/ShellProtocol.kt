package com.ziddy.breakout.shell

/** Wire format shared by the client and the Shizuku user service. */
data class ShellOutcome(val exitCode: Int, val stdout: String, val stderr: String)

object ShellProtocol {
    const val STDOUT_MARK = "---STDOUT---"
    const val STDERR_MARK = "---STDERR---"

    fun format(exitCode: Int, stdout: String, stderr: String): String =
        "EXIT:$exitCode\n$STDOUT_MARK\n$stdout\n$STDERR_MARK\n$stderr"

    fun parse(raw: String): ShellOutcome {
        val exitCode = Regex("EXIT:(-?\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val stdoutStart = raw.indexOf(STDOUT_MARK)
        val stderrStart = raw.indexOf(STDERR_MARK)
        val stdout = if (stdoutStart >= 0) {
            val end = if (stderrStart > stdoutStart) stderrStart else raw.length
            raw.substring(stdoutStart + STDOUT_MARK.length, end).trim()
        } else {
            ""
        }
        val stderr = if (stderrStart >= 0) raw.substring(stderrStart + STDERR_MARK.length).trim() else ""
        return ShellOutcome(exitCode, stdout, stderr)
    }
}
