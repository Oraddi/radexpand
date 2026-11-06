package com.radexpand.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.radexpand.core.Lang
import com.radexpand.core.tx

/** Bildirim panelinde RadExpand ac/kapa kutucugu (D1). */
class QuickTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Repo.init(this)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        Repo.update { it.copy(settings = it.settings.copy(enabled = !it.settings.enabled)) }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        Lang.lang = Lang.defaultFor(Repo.data.value.settings.language)
        val on = Repo.data.value.settings.enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "RadExpand"
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) tx("Açık", "On") else tx("Kapalı", "Off")
        tile.updateTile()
    }
}
