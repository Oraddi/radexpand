package com.radexpand.app

import android.content.Context
import com.radexpand.core.EngineObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Uygulama bazinda uyumluluk istatistigi (Asama 4). YAZILAN METIN TUTULMAZ:
 * yalnizca sayilar, sureler ve uygulama paket adlari.
 */
object CompatStats : EngineObserver {

    @Serializable
    data class AppStat(
        val lookups: Int = 0,
        val editorText: Int = 0,
        val noText: Int = 0,
        val slow: Int = 0,
        val totalMs: Long = 0,
        val maxMs: Long = 0,
        val expansions: Int = 0,
        val undos: Int = 0,
        val lastUsed: Long = 0,
    ) {
        val avgMs: Long get() = if (lookups == 0) 0 else totalMs / lookups
    }

    private val ser = MapSerializer(String.serializer(), AppStat.serializer())
    private val _stats = MutableStateFlow<Map<String, AppStat>>(emptyMap())
    val stats: StateFlow<Map<String, AppStat>> get() = _stats
    private var file: File? = null
    private var dirty = false

    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "compat-stats.json")
        usageFile = File(context.filesDir, "usage.json")
        _usage.value = runCatching { Json.decodeFromString(usageSer, usageFile!!.readText()) }.getOrDefault(emptyMap())
        _stats.value = runCatching { Json.decodeFromString(ser, file!!.readText()) }.getOrDefault(emptyMap())
    }

    private fun edit(pkg: String, f: (AppStat) -> AppStat) {
        val m = _stats.value.toMutableMap()
        m[pkg] = f(m[pkg] ?: AppStat()).copy(lastUsed = System.currentTimeMillis())
        _stats.value = m
        dirty = true
    }

    override fun onLookup(packageName: String, millis: Long, editorGaveText: Boolean) = edit(packageName) {
        it.copy(
            lookups = it.lookups + 1,
            editorText = it.editorText + if (editorGaveText) 1 else 0,
            noText = it.noText + if (editorGaveText) 0 else 1,
            slow = it.slow + if (millis >= 400) 1 else 0,
            totalMs = it.totalMs + millis,
            maxMs = maxOf(it.maxMs, millis),
        )
    }

    override fun onExpansion(packageName: String, trigger: String) {
        edit(packageName) { it.copy(expansions = it.expansions + 1) }
        _usage.value = _usage.value + (trigger to (_usage.value[trigger] ?: 0) + 1)
    }

    override fun onUndo(packageName: String, trigger: String) {
        edit(packageName) { it.copy(undos = it.undos + 1) }
        // Geri alinan genisletme "kullanim" sayilmaz.
        _usage.value[trigger]?.let { n -> _usage.value = _usage.value + (trigger to maxOf(0, n - 1)) }
    }

    // ---- kisayol basina kullanim sayisi (C3): yalnizca sayi, metin yok
    private val usageSer = MapSerializer(String.serializer(), Int.serializer())
    private val _usage = MutableStateFlow<Map<String, Int>>(emptyMap())
    val usage: StateFlow<Map<String, Int>> get() = _usage
    private var usageFile: File? = null

    /** Klavye bir alandan cikinca diske yaz. */
    fun flush() {
        if (!dirty) return
        dirty = false
        runCatching { file?.writeText(Json.encodeToString(ser, _stats.value)) }
        runCatching { usageFile?.writeText(Json.encodeToString(usageSer, _usage.value)) }
    }

    fun resetUsage() {
        _usage.value = emptyMap()
        dirty = true
        flush()
    }

    fun reset() {
        _stats.value = emptyMap()
        dirty = true
        flush()
    }

    /** Bilinen uygulamalarin okunur adlari. */
    fun label(pkg: String): String = when (pkg) {
        "com.android.chrome" -> "Chrome"
        "com.google.android.apps.docs.editors.docs" -> "Google Dokümanlar"
        "com.google.android.apps.docs" -> "Google Drive"
        "com.samsung.android.app.notes" -> "Samsung Notes"
        "com.sec.android.app.sbrowser" -> "Samsung Internet"
        "com.google.android.keep" -> "Google Keep"
        "com.microsoft.office.word" -> "Microsoft Word"
        "com.microsoft.office.officehubrow" -> "Microsoft 365"
        "com.google.android.gm" -> "Gmail"
        "com.whatsapp" -> "WhatsApp"
        "com.radexpand.app" -> "RadExpand (test ekranı)"
        else -> pkg
    }
}
