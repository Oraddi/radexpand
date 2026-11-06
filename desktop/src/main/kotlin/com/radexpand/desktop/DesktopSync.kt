package com.radexpand.desktop

import com.radexpand.core.DataCodec
import com.radexpand.core.RadExpandData
import com.radexpand.core.SyncMerge
import com.radexpand.core.tx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Tablet ile ortak senkron dosyasi (ornegin Google Drive klasorunde radexpand-sync.json).
 * Dosya 5 sn'de bir kontrol edilir; degistiyse birlestirilir. Buradaki degisiklikler 2 sn sonra dosyaya yazilir.
 */
object DesktopSync {
    data class State(val path: String? = null, val lastSync: String? = null, val message: String? = null, val error: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state
    private val exec = Executors.newSingleThreadScheduledExecutor { Thread(it, "radexpand-sync").apply { isDaemon = true } }
    private var lastSeen = 0L
    private var pendingWrite: ScheduledFuture<*>? = null

    fun start() {
        _state.value = State(path = DesktopStore.prefs.value.syncPath)
        exec.scheduleWithFixedDelay({ runCatching { poll() } }, 1, 5, TimeUnit.SECONDS)
        DesktopStore.addListener { scheduleWrite() }
    }

    fun link(f: File) {
        DesktopStore.updatePrefs { it.copy(syncPath = f.absolutePath) }
        _state.value = _state.value.copy(path = f.absolutePath)
        lastSeen = 0
        syncNow()
    }

    fun unlink() {
        DesktopStore.updatePrefs { it.copy(syncPath = null) }
        _state.value = State()
    }

    fun syncNow() = exec.execute { doSync() }

    private fun poll() {
        val f = _state.value.path?.let(::File) ?: return
        if (f.exists() && f.lastModified() != lastSeen) doSync()
    }

    private fun scheduleWrite() {
        val p = _state.value.path ?: return
        pendingWrite?.cancel(false)
        pendingWrite = exec.schedule({
            runCatching { write(File(p), DesktopStore.data.value) }
                .onSuccess { done(null) }.onFailure { fail(tx("Dosyaya yazılamadı: ", "Could not write the file: ") + it.message) }
        }, 2, TimeUnit.SECONDS)
    }

    private fun doSync() {
        val f = _state.value.path?.let(::File) ?: return
        try {
            var msg = tx("Dosya boştu; bu bilgisayarın kısayolları yazıldı.", "The file was empty; this computer's shortcuts were written.")
            var remoteSame = false
            if (f.exists() && f.length() > 0) {
                val remote = DataCodec.decode(f.readText(Charsets.UTF_8))
                val r = SyncMerge.merge(DesktopStore.data.value.shortcuts, remote.shortcuts)
                if (r.changed) {
                    if (DesktopStore.data.value.settings.backupBeforeSync) DesktopStore.backup("senkron-oncesi")
                    DesktopStore.update { it.copy(shortcuts = r.shortcuts) }
                }
                remoteSame = remote.shortcuts.sortedBy { it.id } == DesktopStore.data.value.shortcuts.sortedBy { it.id }
                msg = tx("Diğer cihazdan ${r.fromRemote} değişiklik alındı, bu bilgisayardan ${r.fromLocal} gönderildi.", "${r.fromRemote} changes received from the other device, ${r.fromLocal} sent from this computer.")
            }
            // Dosya zaten guncelse yazma (Drive gibi araclarin tarih degisikligiyle sonsuz dongu olmasin).
            if (remoteSame) lastSeen = f.lastModified() else write(f, DesktopStore.data.value)
            done(msg)
        } catch (e: Exception) {
            lastSeen = f.lastModified() // bozuk dosyada surekli denememek icin
            fail(tx("Senkron başarısız: ", "Sync failed: ") + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun write(f: File, d: RadExpandData) {
        f.parentFile?.mkdirs()
        f.writeText(DataCodec.encode(RadExpandData(shortcuts = d.shortcuts)), Charsets.UTF_8)
        lastSeen = f.lastModified()
    }

    private fun done(msg: String?) {
        val t = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())
        _state.value = _state.value.copy(lastSync = t, message = msg ?: _state.value.message, error = false)
    }

    private fun fail(msg: String) {
        _state.value = _state.value.copy(message = msg, error = true)
        DesktopDiag.log("SENKRON hata")
    }
}
