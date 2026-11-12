package dev.isa.bypass

import kotlin.system.exitProcess

/** Runs inside the Shizuku server process (shell uid, or root if Shizuku was started as root). */
class ShellService : IShellService.Stub() {
    override fun destroy() { exitProcess(0) }

    override fun exec(cmd: String): String {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }
}
