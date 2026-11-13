package com.radexpand.desktop

import com.radexpand.core.AppSettings
import com.radexpand.core.DataCodec
import com.radexpand.core.RadExpandData
import com.radexpand.ui.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class DesktopPrefs(
    val syncPath: String? = null,
    val startWithWindows: Boolean = false,
    /** "Drive'a gonder / Drive'dan al" bu bilgisayarda en az bir kez kullanildi mi (hatirlatma yalnizca o zaman). */
    val manualSyncUsed: Boolean = false,
    /** Son gonderilen / alinan kisayol listesinin parmak izi ([DriveShare.stamp]). */
    val lastSharedStamp: String? = null,
)

/**
 * Windows'ta veri: %APPDATA%\RadExpand\radexpand.json (+ backups\). Atomik yazma ve otomatik yedek.
 * Android ile ayni dosya bicimi.
 */
object DesktopStore : DataStore {
    /** RADEXPAND_HOME ortam degiskeni: test icin ayri veri klasoru. */
    val dir = (System.getenv("RADEXPAND_HOME")?.let(::File) ?: File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "RadExpand")).apply { mkdirs() }
    private val file = File(dir, "radexpand.json")
    private val prefsFile = File(dir, "desktop.json")
    private val backupDir = File(dir, "backups").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val _data = MutableStateFlow(load())
    override val data: StateFlow<RadExpandData> get() = _data

    private val _prefs = MutableStateFlow(runCatching { json.decodeFromString(DesktopPrefs.serializer(), prefsFile.readText()) }.getOrDefault(DesktopPrefs()))
    val prefs: StateFlow<DesktopPrefs> get() = _prefs

    var lastError: String? = null
        private set

    private var listeners = listOf<(RadExpandData) -> Unit>()

    private fun load(): RadExpandData {
        // Ilk calisma: Windows'ta tiklamadan sonraki ilk kelime kelime basi sayilir (Espanso gibi).
        if (!file.exists()) {
            // Eski adla (MedExpand 0.1-0.2) kaydedilmis kisayollar varsa bir kez aktar; eski dosyaya dokunulmaz.
            val legacy = File(System.getenv("APPDATA") ?: "", "MedExpand\\medexpand.json")
            if (System.getenv("RADEXPAND_HOME") == null && legacy.isFile) {
                runCatching { DataCodec.decode(legacy.readText(Charsets.UTF_8)) }.getOrNull()?.let { old ->
                    return RadExpandData(shortcuts = old.shortcuts, settings = AppSettings(unknownStartIsBoundary = true))
                }
            }
            return RadExpandData(settings = AppSettings(unknownStartIsBoundary = true))
        }
        return try {
            DataCodec.decode(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            lastError = com.radexpand.core.tx("Veri dosyası okunamadı; en son yedek açıldı.", "The data file could not be read; the latest backup was opened.")
            file.copyTo(File(backupDir, "bozuk-${stamp()}.json"), overwrite = true)
            backups().firstNotNullOfOrNull { runCatching { DataCodec.decode(it.readText()) }.getOrNull() }
                ?: RadExpandData(settings = AppSettings(unknownStartIsBoundary = true))
        }
    }

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

    private fun save(d: RadExpandData) {
        val tmp = File(dir, "radexpand.json.tmp")
        tmp.writeText(DataCodec.encode(d), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun updatePrefs(f: (DesktopPrefs) -> DesktopPrefs) {
        _prefs.value = f(_prefs.value)
        prefsFile.writeText(json.encodeToString(DesktopPrefs.serializer(), _prefs.value))
    }

    fun backups(): List<File> =
        backupDir.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("bozuk") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    @Synchronized
    override fun backup(reason: String) = writeBackup(_data.value, reason)

    private fun writeBackup(d: RadExpandData, reason: String) {
        if (d.shortcuts.isEmpty()) return
        File(backupDir, "radexpand-${stamp()}-$reason.json").writeText(DataCodec.encode(d))
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
