package dev.isa.bypass

import android.app.Activity
import android.os.Bundle
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var key: EditText
    private val B = "/sys/class/power_supply/battery"

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        key = EditText(this).apply { hint = "settings key"; setText(Bypass.key(this@MainActivity)); setSingleLine() }
        log = TextView(this).apply { setTextIsSelectable(true); setPadding(0, 24, 0, 0) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            listOf(
                btn("1. Grant Shizuku access") { grantShizuku() },
                btn("2. Grant secure-settings permission (one time)") { grantSecure() },
                key,
                btn("Save key") { Bypass.saveKey(this@MainActivity, key.text.toString().trim()); say("Saved.\n" + state()) },
                btn("Test bypass ON") { test(true) },
                btn("Test bypass OFF") { test(false) },
                btn("Show status") { status() },
                log,
            ).forEach(::addView)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        Shizuku.addRequestPermissionResultListener { _, _ -> say(state()) }
    }

    override fun onResume() { super.onResume(); say(state()) }

    private fun state(): String {
        val shz = when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> "not running"
            !Sh.ready() -> "running, no permission"
            else -> "ready"
        }
        val sec = if (Bypass.direct(this)) "granted (tile works without Shizuku)" else "not granted"
        return "Shizuku: $shz\nWRITE_SECURE_SETTINGS: $sec"
    }

    private fun say(t: String) = runOnUiThread { log.text = t }

    private fun grantShizuku() {
        try { Shizuku.requestPermission(0) } catch (e: Throwable) { say("Shizuku is not running. Start it first.") }
    }

    private fun grantSecure() {
        if (!Sh.ready()) return say("Needs Shizuku first.\n" + state())
        Sh.run(this, "pm grant $packageName $SECURE 2>&1; echo done") { out -> say("pm grant: $out\n" + state()) }
    }

    private fun test(on: Boolean) {
        if (!Bypass.usable(this)) return say("Grant Shizuku access or the secure-settings permission first.\n" + state())
        Bypass.set(this, on) { v ->
            Sh.runOrSkip(this, "sleep 2; echo \"status=\$(cat $B/status) current_now=\$(cat $B/current_now)\"") { bat ->
                say("${key.text} now = $v\n$bat")
            }
        }
    }

    private fun status() {
        Bypass.get(this) { v ->
            Sh.runOrSkip(this, "echo \"status=\$(cat $B/status) current_now=\$(cat $B/current_now) capacity=\$(cat $B/capacity)\"") { bat ->
                say("${key.text} = $v\n$bat\n" + state())
            }
        }
    }
}
