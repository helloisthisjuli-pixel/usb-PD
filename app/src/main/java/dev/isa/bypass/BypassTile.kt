package dev.isa.bypass

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BypassTile : TileService() {
    private val ui = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        Bypass.get(this) { v -> ui.post { paint(v) } }
    }

    override fun onClick() {
        if (!Bypass.usable(this)) { openApp(); return }
        Bypass.get(this) { cur ->
            Bypass.set(this, cur != 1) { v -> ui.post { paint(v) } }
        }
    }

    private fun paint(v: Int?) {
        val (st, sub) = when (v) {
            1 -> Tile.STATE_ACTIVE to "Bypass on"
            0 -> Tile.STATE_INACTIVE to "Off"
            else -> Tile.STATE_INACTIVE to "Setup needed"
        }
        qsTile?.apply { state = st; label = "Bypass charging"; subtitle = sub; updateTile() }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else startActivityAndCollapse(i)
    }
}
