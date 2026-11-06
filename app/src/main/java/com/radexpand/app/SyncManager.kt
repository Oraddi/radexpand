package com.radexpand.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.radexpand.core.DataCodec
import com.radexpand.core.EspansoExport
import com.radexpand.core.RadExpandData
import com.radexpand.core.SyncMerge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Windows RadExpand ile ortak senkron dosyasi (RadExpand JSON) ve istege bagli Espanso YAML ciktisi.
 *
 * RadExpand'in INTERNET izni yoktur: dosya, Android'in dosya secicisiyle secilen bir klasorde durur
 * (ornegin Google Drive ya da Syncthing klasoru). Agla ilgili isi o uygulama yapar.
 *
 * Kural: ayni kisayolda son degistirilen kazanir; hicbir kayit sessizce silinmez (bkz. SyncMerge).
 */
object SyncManager {
    private const val PREFS = "sync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var writeJob: Job? = null
    private lateinit var ctx: Context

    data class State(
        val syncUri: Uri? = null,
        val syncName: String? = null,
        val yamlUri: Uri? = null,
        val yamlName: String? = null,
        val lastSync: String? = null,
        val message: String? = null,
        val error: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state

    private var lastRead = 0L

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = State(
            syncUri = p.getString("syncUri", null)?.let(Uri::parse),
            syncName = p.getString("syncName", null),
            yamlUri = p.getString("yamlUri", null)?.let(Uri::parse),
            yamlName = p.getString("yamlName", null),
            lastSync = p.getString("lastSync", null),
        )
        // Her degisiklikten 3 sn sonra dosyalara yaz (art arda degisiklikler tek yazmada birlesir).
        Repo.addListener { scheduleWrite() }
    }

    fun linkSync(uri: Uri) = link(uri, "sync") { s, name -> s.copy(syncUri = uri, syncName = name) }.also { syncNow() }
    fun linkYaml(uri: Uri) = link(uri, "yaml") { s, name -> s.copy(yamlUri = uri, yamlName = name) }.also { scheduleWrite(0) }

    private fun link(uri: Uri, key: String, set: (State, String) -> State) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = displayName(uri)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${key}Uri", uri.toString()).putString("${key}Name", name).apply()
        _state.value = set(_state.value, name)
    }

    fun unlinkSync() = unlink("sync") { it.copy(syncUri = null, syncName = null) }
    fun unlinkYaml() = unlink("yaml") { it.copy(yamlUri = null, yamlName = null) }

    private fun unlink(key: String, set: (State) -> State) {
        val s = _state.value
        val uri = if (key == "sync") s.syncUri else s.yamlUri
        uri?.let { runCatching { ctx.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("${key}Uri").remove("${key}Name").apply()
        _state.value = set(s)
    }

    /** Uygulama acilinca / klavye calismaya baslayinca: dosyadaki degisiklikleri al (en fazla 5 dk'da bir). */
    fun syncIfStale() {
        if (_state.value.syncUri == null) return
        if (System.currentTimeMillis() - lastRead < Repo.data.value.settings.syncCheckMinutes.coerceAtLeast(1) * 60_000L) return
        syncNow()
    }

    /** Dosyayi oku, birlestir, sonucu dosyaya geri yaz. */
    fun syncNow() {
        val uri = _state.value.syncUri ?: return
        scope.launch {
            mutex.withLock {
                try {
                    lastRead = System.currentTimeMillis()
                    val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    var msg = com.radexpand.core.tx("Dosya boştu; tablet verisi yazıldı.", "The file was empty; the tablet data was written.")
                    if (text.isNotBlank()) {
                        val remote = DataCodec.decode(text)
                        val r = SyncMerge.merge(Repo.data.value.shortcuts, remote.shortcuts)
                        if (r.changed) {
                            if (Repo.data.value.settings.backupBeforeSync) Repo.backup("senkron-oncesi")
                            Repo.update { it.copy(shortcuts = r.shortcuts) }
                        }
                        msg = com.radexpand.core.tx("Diğer cihazdan ${r.fromRemote} değişiklik alındı, bu cihazdan ${r.fromLocal} gönderildi.", "${r.fromRemote} changes received from the other device, ${r.fromLocal} sent from this one.")
                    }
                    writeSync(uri, Repo.data.value)
                    done(msg)
                } catch (e: Exception) {
                    fail(com.radexpand.core.tx("Senkron başarısız: ", "Sync failed: ") + (e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun scheduleWrite(delayMs: Long = 3000) {
        val s = _state.value
        if (s.syncUri == null && s.yamlUri == null) return
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(delayMs)
            mutex.withLock {
                try {
                    val d = Repo.data.value
                    s.syncUri?.let { writeSync(it, d) }
                    _state.value.yamlUri?.let { u ->
                        ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(EspansoExport.export(d.shortcuts, includeDisabled = d.settings.exportDisabled).toByteArray()) }
                    }
                    done(null)
                } catch (e: Exception) {
                    fail(com.radexpand.core.tx("Dosyaya yazılamadı: ", "Could not write file: ") + (e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun writeSync(uri: Uri, d: RadExpandData) {
        // Senkron dosyasina yalnizca kisayollar yazilir; cihaz ayarlari her cihazda ayridir.
        val out = RadExpandData(shortcuts = d.shortcuts)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(DataCodec.encode(out).toByteArray()) }
            ?: error("dosya yazmak için açılamadı")
    }

    private fun done(msg: String?) {
        val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lastSync", now).apply()
        _state.value = _state.value.copy(lastSync = now, message = msg ?: _state.value.message, error = false)
    }

    private fun fail(msg: String) {
        _state.value = _state.value.copy(message = msg, error = true)
        DiagLog.log("SENKRON hata")
    }

    fun displayName(uri: Uri): String =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
}
