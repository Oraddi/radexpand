package com.radexpand.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.radexpand.desktop.ui.DesktopRoot
import com.radexpand.core.Lang
import com.radexpand.core.tx
import com.radexpand.ui.RadTheme
import com.radexpand.ui.UiLang
import com.radexpand.ui.t
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/** Uygulama simgesi (Android simgesiyle ayni: klavye tusu + genisleme oku + imlec). */
val AppIcon: ImageVector = ImageVector.Builder("radexpand", 108.dp, 108.dp, 108f, 108f).apply {
    path(fill = SolidColor(Color(0xFF00796B))) {
        moveTo(20f, 0f); lineTo(88f, 0f); arcTo(20f, 20f, 0f, false, true, 108f, 20f); lineTo(108f, 88f)
        arcTo(20f, 20f, 0f, false, true, 88f, 108f); lineTo(20f, 108f); arcTo(20f, 20f, 0f, false, true, 0f, 88f)
        lineTo(0f, 20f); arcTo(20f, 20f, 0f, false, true, 20f, 0f); close()
    }
    path(fill = SolidColor(Color.White)) {
        moveTo(26f, 32f); horizontalLineTo(82f); arcTo(8f, 8f, 0f, false, true, 90f, 40f); verticalLineTo(68f)
        arcTo(8f, 8f, 0f, false, true, 82f, 76f); horizontalLineTo(26f); arcTo(8f, 8f, 0f, false, true, 18f, 68f)
        verticalLineTo(40f); arcTo(8f, 8f, 0f, false, true, 26f, 32f); close()
    }
    path(stroke = SolidColor(Color(0xFF00796B)), strokeLineWidth = 6f, strokeLineCap = StrokeCap.Round) {
        moveTo(30f, 54f); horizontalLineTo(58f); moveTo(50f, 46f); lineTo(58f, 54f); lineTo(50f, 62f)
        moveTo(72f, 42f); verticalLineTo(66f)
    }
}.build()

private var instanceLock: FileLock? = null

/** Ayni anda tek RadExpand calissin (iki kanca = iki kez genisletme). */
private fun acquireSingleInstance(): Boolean {
    val f = File(DesktopStore.dir, ".lock")
    return try {
        instanceLock = RandomAccessFile(f, "rw").channel.tryLock()
        instanceLock != null
    } catch (_: Exception) {
        false
    }
}

fun main(args: Array<String>) {
    Lang.lang = Lang.defaultFor(DesktopStore.data.value.settings.language)
    if (!acquireSingleInstance()) {
        JOptionPane.showMessageDialog(null, tx("RadExpand zaten çalışıyor (sistem tepsisindeki simgeye bakın).", "RadExpand is already running (see the icon in the system tray)."), "RadExpand", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    Host.start(DesktopStore)
    DesktopSync.start()
    EspansoWatch.start()
    Updater.start()
    UiLang.lang = Lang.lang

    application {
        var visible by remember { mutableStateOf("--minimized" !in args) }
        val icon = rememberVectorPainter(AppIcon)
        val data by DesktopStore.data.collectAsState()
        fun quit() {
            Host.stop()
            exitApplication()
        }
        Tray(
            icon = icon,
            tooltip = if (Host.paused) t("RadExpand (duraklatıldı)", "RadExpand (paused)") else "RadExpand",
            onAction = { visible = true },
            menu = {
                Item(t("RadExpand'i aç", "Open RadExpand"), onClick = { visible = true })
                Item(if (Host.paused) t("Devam et", "Resume") else t("Duraklat", "Pause"), onClick = { Host.paused = !Host.paused })
                Separator()
                Item(t("Çıkış", "Exit"), onClick = { quit() })
            },
        )
        // Kisayol arama paneli (Alt+Space)
        val search by Host.search.collectAsState()
        search?.let { req -> androidx.compose.runtime.key(req) { com.radexpand.desktop.ui.SearchWindow(req, data.settings) } }
        Window(
            onCloseRequest = { visible = false }, // kapatinca tepside calismaya devam eder
            visible = visible,
            title = "RadExpand",
            icon = icon,
            state = rememberWindowState(width = 1180.dp, height = 820.dp),
        ) {
            RadTheme(data.settings) {
                DesktopRoot(onHide = { visible = false }, onExit = { quit() })
            }
        }
    }
}
