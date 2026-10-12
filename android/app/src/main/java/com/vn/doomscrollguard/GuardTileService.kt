package com.vn.doomscrollguard

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile: the same one-tap switch, one swipe away.
 * API 24+. State mirrors GuardState (the shared SharedPreferences source of truth).
 */
@RequiresApi(Build.VERSION_CODES.N)
class GuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val on = GuardState.toggle(this)
        qsTile?.let {
            it.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.label = if (on) "Guard ON" else "Guard OFF"
            it.updateTile()
        }
        // Keep the tile panel open so the user sees the state flip.
    }

    private fun refresh() {
        val on = GuardState.isOn(this)
        qsTile?.let {
            it.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.label = if (on) "Guard ON" else "Guard OFF"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.subtitle = if (on) "Shorts & Reels blocked" else "Everything allowed"
            }
            it.icon = Icon.createWithResource(this, android.R.drawable.ic_lock_idle_lock)
            it.updateTile()
        }
    }
}
