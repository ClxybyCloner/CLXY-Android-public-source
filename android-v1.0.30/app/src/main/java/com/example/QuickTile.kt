package com.example

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/** The Quick Settings tile: turns the CLXY VPN/proxy on or off without opening the app. */
class QuickTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        show()
    }

    override fun onClick() {
        super.onClick()
        if (CoreService.on) {
            CoreService.turnOff(this)
        } else if (CoreService.usesVpn(this) && VpnService.prepare(this) != null) {
            openApp()
        } else {
            try {
                CoreService.turnOn(this)
            } catch (e: Exception) {
                Log.w("QuickTile", "starting the service failed; opening the app", e)
                openApp()
            }
        }
        show()
    }

    private fun show() {
        val tile = qsTile ?: return
        tile.state = if (CoreService.on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_logo)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun refresh(context: Context) {
            TileService.requestListeningState(context, ComponentName(context, QuickTile::class.java))
        }
    }
}
