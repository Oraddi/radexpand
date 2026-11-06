package com.radexpand.app

import android.content.Context
import android.util.AtomicFile
import com.radexpand.core.DataCodec
import com.radexpand.core.RadExpandData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kisayollar + ayarlar. Tek dosya (files/radexpand.json), atomik yazma ve otomatik yedekler.
 * Klavye servisi ve ekranlar ayni surecte calistigi icin ayni nesneyi paylasir.
 */
object Repo : com.radexpand.ui.DataStore {
    private const val FILE = "radexpand.json"
    private const val MAX_BACKUPS = 30

    private lateinit var appContext: Context
    private val _data = MutableStateFlow(RadExpandData())
    override val data: StateFlow<RadExpandData> get() = _data

    /** Son yukleme/kaydetme hatasi (kullaniciya gosterilir). */
    @Volatile
    var lastError: String? = null
        private set

    private var listeners = listOf<(RadExpandData) -> Unit>()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _data.value = load()
    }

    val isReady get() = ::appContext.isInitialized

    private fun file() = AtomicFile(File(appContext.filesDir, FILE))
    private fun backupDir() = File(appContext.filesDir, "backups").apply { mkdirs() }

    private fun load(): RadExpandData {
        val f = file()
        if (!f.baseFile.exists()) return RadExpandData()
        return try {
            DataCodec.decode(String(f.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // Ana dosya bozuksa en yeni yedekten ac; bozuk dosyayi da sakla.
            lastError = "Veri dosyası okunamadı (${e.javaClass.simpleName}); en son yedek açıldı."
            f.baseFile.copyTo(File(backupDir(), "bozuk-${stamp()}.json"), overwrite = true)
            backups().firstNotNullOfOrNull { b -> runCatching { DataCodec.decode(b.readText()) }.getOrNull() } ?: RadExpandData()
        }
    }

    /** Veriyi degistirir, kaydeder ve dinleyicilere bildirir. */
    @Synchronized
    override fun update(transform: (RadExpandData) -> RadExpandData): RadExpandData {
        val old = _data.value
        val new = transform(old)
        if (new == old) return old
        dailyBackupIfNeeded(old)
        save(new)
        _data.value = new
        listeners.forEach { runCatching { it(new) } }
        return new
    }

    @Synchronized
    fun addListener(l: (RadExpandData) -> Unit) {
        listeners = listeners + l
    }

    @Synchronized
    fun removeListener(l: (RadExpandData) -> Unit) {
        listeners = listeners - l
    }

    private fun save(d: RadExpandData) {
        val f = file()
        val out = f.startWrite()
        try {
            out.write(DataCodec.encode(d).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
            lastError = null
        } catch (e: Exception) {
            f.failWrite(out)
            lastError = "Kaydedilemedi: ${e.message}"
            throw e
        }
    }

    // ------------------------------------------------------------------ yedekler

    fun backups(): List<File> =
        backupDir().listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("bozuk") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Simdiki verinin yedegini al (ice aktarma, geri yukleme, senkron oncesi). */
    @Synchronized
    override fun backup(reason: String) = writeBackup(_data.value, reason)

    private fun writeBackup(d: RadExpandData, reason: String) {
        if (d.shortcuts.isEmpty()) return
        File(backupDir(), "radexpand-${stamp()}-$reason.json").writeText(DataCodec.encode(d))
        backups().drop(_data.value.settings.maxBackups.coerceIn(5, 500)).forEach { it.delete() }
    }

    private fun dailyBackupIfNeeded(d: RadExpandData) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (backups().none { it.name.startsWith("radexpand-$today") }) writeBackup(d, "gunluk")
    }

    @Synchronized
    fun restoreBackup(f: File) {
        val d = DataCodec.decode(f.readText())
        backup("geri-yukleme-oncesi")
        update { cur -> d.copy(settings = cur.settings) }
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
