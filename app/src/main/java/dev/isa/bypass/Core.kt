package dev.isa.bypass

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import rikka.shizuku.Shizuku

const val SECURE = "android.permission.WRITE_SECURE_SETTINGS"

object Sh {
    private var svc: IShellService? = null
    private var binding = false
    private val waiting = mutableListOf<() -> Unit>()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            svc = IShellService.Stub.asInterface(b)
            binding = false
            val jobs = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
            jobs.forEach { it() }
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null; binding = false }
    }

    fun ready() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    /** Like [run], but if Shizuku isn't ready just returns a note instead of failing. */
    fun runOrSkip(ctx: Context, cmd: String, cb: (String) -> Unit) {
        if (ready()) run(ctx, cmd, cb) else cb("(battery readout needs Shizuku; check the battery icon / charging status instead)")
    }

    /** Runs [cmd] through Shizuku on a background thread; [cb] is called off the main thread. */
    fun run(ctx: Context, cmd: String, cb: (String) -> Unit) {
        val job = { Thread { cb(try { svc!!.exec(cmd) } catch (e: Exception) { "ERR ${e.message}" }) }.start() }
        if (svc != null) { job(); return }
        synchronized(waiting) { waiting += job }
        if (!binding) {
            binding = true
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
                .processNameSuffix("sh").daemon(false).version(1)
            Shizuku.bindUserService(args, conn)
        }
    }
}

/**
 * Samsung's "Pause USB Power Delivery" is driven by the system setting `pass_through` (1 = bypass, 0 = normal),
 * the same switch ONDER1E/usbpdbs flips with `settings put system pass_through <0|1>`.
 *
 * Preferred path: once this app holds WRITE_SECURE_SETTINGS (granted one time via Shizuku) it writes the setting
 * directly, so the tile keeps working even when Shizuku is stopped. Fallback: run `settings put` through Shizuku.
 */
object Bypass {
    private fun prefs(c: Context) = c.getSharedPreferences("cfg", 0)
    fun key(c: Context) = prefs(c).getString("key", "pass_through") ?: "pass_through"
    fun saveKey(c: Context, k: String) = prefs(c).edit().putString("key", k).apply()

    fun direct(c: Context) = c.checkSelfPermission(SECURE) == PackageManager.PERMISSION_GRANTED
    fun usable(c: Context) = direct(c) || Sh.ready()

    /** 1 = bypass on, 0 = off, null = can't tell (no permission and Shizuku not ready, or error). */
    fun get(c: Context, cb: (Int?) -> Unit) {
        val k = key(c)
        when {
            direct(c) -> Thread { cb(Settings.System.getString(c.contentResolver, k)?.trim()?.toIntOrNull() ?: 0) }.start()
            Sh.ready() -> Sh.run(c, "settings get system $k") { cb(if (it.trim() == "null") 0 else it.trim().toIntOrNull()) }
            else -> cb(null)
        }
    }

    /** Sets bypass and returns the value read back afterwards. */
    fun set(c: Context, on: Boolean, cb: (Int?) -> Unit) {
        val k = key(c); val v = if (on) 1 else 0
        when {
            direct(c) -> Thread {
                runCatching { Settings.System.putInt(c.contentResolver, k, v) }
                cb(Settings.System.getString(c.contentResolver, k)?.trim()?.toIntOrNull())
            }.start()
            Sh.ready() -> Sh.run(c, "settings put system $k $v; settings get system $k") { cb(it.lines().last().trim().toIntOrNull()) }
            else -> cb(null)
        }
    }
}
