package com.radexpand.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Baska bir metin genisletici de calisiyorsa uyar (Espanso ya da eski MedExpand surumu):
 * ikisi birlikte calisirsa ayni kisayol iki kez genisler ve metin karisir.
 */
object EspansoWatch {
    private val others = mapOf("espanso.exe" to "Espanso", "espansod.exe" to "Espanso", "medexpand.exe" to "MedExpand")
    private val _running = MutableStateFlow<String?>(null)
    /** Calisan diger programin adi; yoksa null. */
    val running: StateFlow<String?> get() = _running

    fun start() {
        Executors.newSingleThreadScheduledExecutor { Thread(it, "radexpand-espanso").apply { isDaemon = true } }
            .scheduleWithFixedDelay({
                _running.value = runCatching {
                    ProcessHandle.allProcesses().map { p -> p.info().command().map { File(it).name.lowercase() }.orElse("") }
                        .filter { it in others }.findFirst().map { others[it] }.orElse(null)
                }.getOrNull()
            }, 0, 10, TimeUnit.SECONDS)
    }
}

/** Windows ile birlikte baslat (HKCU\...\Run). Yalnizca paketlenmis .exe ile calisirken mumkun. */
object Autostart {
    private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "RadExpand"

    val exePath: String? get() = System.getProperty("jpackage.app-path")

    fun isEnabled(): Boolean = runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, NAME) }.getOrDefault(false)

    fun set(on: Boolean): Boolean = runCatching {
        if (on) {
            val exe = exePath ?: return false
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, NAME, "\"$exe\" --minimized")
        } else if (isEnabled()) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, NAME)
        }
        true
    }.getOrDefault(false)
}

object Files2 {
    fun openMany(title: String, startDir: File?): List<File> {
        val fd = FileDialog(null as Frame?, title, FileDialog.LOAD)
        fd.isMultipleMode = true
        if (startDir?.isDirectory == true) fd.directory = startDir.absolutePath
        fd.isVisible = true
        return fd.files?.toList() ?: emptyList()
    }

    fun save(title: String, defaultName: String, startDir: File? = null): File? {
        val fd = FileDialog(null as Frame?, title, FileDialog.SAVE)
        fd.file = defaultName
        if (startDir?.isDirectory == true) fd.directory = startDir.absolutePath
        fd.isVisible = true
        val name = fd.file ?: return null
        return File(fd.directory, name)
    }

    val espansoMatchDir: File get() = File(System.getenv("APPDATA") ?: "", "espanso\\match")
}
