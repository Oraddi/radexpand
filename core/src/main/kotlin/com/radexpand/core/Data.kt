package com.radexpand.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Kalici kisayol kaydi (RadExpand'in kendi dosya bicimi). Tablet ve Windows ayni bicimi kullanir.
 * Silinen kayitlar senkron icin "mezar tasi" (deleted=true) olarak kalir.
 */
@Serializable
data class ShortcutRecord(
    val id: String = newId(),
    val trigger: String,
    val replacement: String,
    val mode: ExpandMode = ExpandMode.MANUAL,
    val wordBoundary: Boolean = true,
    val propagateCase: Boolean = true,
    val enabled: Boolean = true,
    val category: String = "",
    /** Son degisiklik zamani (epoch ms). Senkronda yenisi kazanir. */
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    /** Ice aktarma notu vb. (ornegin "Espanso: word yoktu"). */
    val note: String = "",
) {
    fun toShortcut() = Shortcut(trigger, replacement, mode, wordBoundary, enabled && !deleted, category, propagateCase)

    /** Davranisi etkileyen icerik ayni mi (id/zaman/not haric)? */
    fun sameContent(o: ShortcutRecord) =
        trigger == o.trigger && replacement == o.replacement && mode == o.mode &&
            wordBoundary == o.wordBoundary && propagateCase == o.propagateCase && enabled == o.enabled &&
            category == o.category && deleted == o.deleted

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Cihaza ozel ayarlar (senkronlanmaz, tam yedekte yer alir). Hepsi Ayarlar ekranindan degistirilebilir.
 * Yeni alanlarin hepsinin varsayilani vardir: eski dosyalar sorunsuz okunur.
 */
@Serializable
data class AppSettings(
    // ---- genel
    val enabled: Boolean = true,
    /** Arayuz dili: "tr", "en"; bos = henuz secilmedi (ilk acilista sorulur). */
    val language: String = "",
    /** Ilk acilis sihirbazi tamamlandi mi. */
    val onboardingDone: Boolean = false,

    // ---- tuslar (A1, A2)
    val spaceAction: KeyAction = KeyAction.EXPAND_WITH_KEY,
    val enterAction: KeyAction = KeyAction.EXPAND_WITH_KEY,
    val tabAction: KeyAction = KeyAction.EXPAND_ONLY,
    /** Kullanicinin atadigi ozel genisletme tusu ("kod:degistiriciler", platforma ozgu); null = yok. */
    val specialKey: String? = null,
    /** Ozel tusun ekranda gorunen adi (ornegin "F2", "Ctrl+Space"). */
    val specialKeyLabel: String = "",
    /** "Yalnizca Tab" kisayollari Tab ile de genislesin mi (kapaliysa yalnizca ozel tusla). */
    val tabForSpecialOnly: Boolean = true,

    // ---- kelime davranislari (A3, A4)
    val apostropheIsWord: Boolean = true,
    val hyphenIsWord: Boolean = false,
    val slashIsWord: Boolean = false,
    val caseMode: CaseMode = CaseMode.FULL,
    val minUpperLetters: Int = 3,
    /** Yeni kisayollarin varsayilan buyuk harf davranisi. */
    val defaultPropagateCase: Boolean = true,
    val punctuationTriggers: Boolean = false,
    val punctuationChars: String = ".,;:!?",
    val undoWithBackspace: Boolean = true,

    // ---- gorunum (A5)
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.TEAL,
    val textScale: Float = 1.0f,

    // ---- ice / disa aktarma, yedek, senkron (A6, A7)
    val espansoNoWordMode: NoWordImport = NoWordImport.AUTO_WORD_START,
    val defaultConflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_EXISTING,
    val maxBackups: Int = 30,
    val exportDisabled: Boolean = true,
    val syncCheckMinutes: Int = 5,
    val backupBeforeSync: Boolean = true,

    // ---- guvenlik ve uyumluluk (A8)
    val allowBlindMode: Boolean = true,
    val expandInPasswordFields: Boolean = false,
    val diagnosticsEnabled: Boolean = true,
    val disabledPackages: Set<String> = emptySet(),
    val blindPackages: Set<String> = emptySet(),

    // ---- Windows (A9)
    /** Tiklama/pencere degisiminden sonraki ilk kelimeyi kelime basi say (Espanso gibi). */
    val unknownStartIsBoundary: Boolean = false,
    /** Genel duraklat/devam kisayolu ("kod:degistiriciler"); null = yok. */
    val pauseHotkey: String? = null,
    val pauseHotkeyLabel: String = "",
    /** Yavas programlar icin tuslar arasi bekleme (ms). */
    val typingDelayMs: Int = 0,
    /** Bu uzunluktan uzun metinler pano ile yapistirilsin (0 = hic). */
    val pasteLongerThan: Int = 0,
    val checkUpdates: Boolean = true,

    // ---- Android
    val onKeyboardDisconnect: DisconnectAction = DisconnectAction.SHOW_BAR,
) {
    fun toEngineConfig(): EngineConfig {
        val keyActions = mapOf(
            TriggerKey.SPACE to spaceAction,
            TriggerKey.ENTER to enterAction,
            TriggerKey.TAB to tabAction,
            TriggerKey.SPECIAL to if (specialKey != null) KeyAction.EXPAND_ONLY else KeyAction.OFF,
        )
        return EngineConfig(
            enabled = enabled,
            expandInPasswordFields = expandInPasswordFields,
            undoWithBackspace = undoWithBackspace,
            allowBlindMode = allowBlindMode,
            keyActions = keyActions,
            manualKeys = keyActions.filterValues { it != KeyAction.OFF }.keys,
            specialKeys = buildSet {
                if (tabForSpecialOnly && tabAction != KeyAction.OFF) add(TriggerKey.TAB)
                if (specialKey != null) add(TriggerKey.SPECIAL)
            },
            punctuationTriggers = punctuationTriggers,
            punctuationChars = punctuationChars,
            disabledPackages = disabledPackages,
            blindPackages = blindPackages,
            unknownStartIsBoundary = unknownStartIsBoundary,
            words = WordRules(apostropheIsWord, hyphenIsWord, slashIsWord),
            caseMode = caseMode,
            minUpperLetters = minUpperLetters.coerceIn(1, 10),
        )
    }
}

/** Bir tetik tusunun davranisi (A1). */
@Serializable
enum class KeyAction {
    /** Bu tus genisletmez, normal calisir. */
    OFF,

    /** Genislet ve tusun kendi isini de yap (Space: bosluk, Enter: yeni satir, Tab: sekme/sonraki alan). */
    EXPAND_WITH_KEY,

    /** Yalnizca genislet; tusun kendi isi yapilmaz (Tab varsayilani: ek yazmak icin imlec sonda kalir). */
    EXPAND_ONLY,
}

@Serializable
enum class CaseMode { OFF, CAPITAL, FULL }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class Accent { TEAL, BLUE, PURPLE, ORANGE, GREEN, DYNAMIC }

/** Espanso'da `word` olmayan kisayollar nasil aktarilsin (A6). */
@Serializable
enum class NoWordImport { AUTO_WORD_START, AUTO_ANYWHERE, MANUAL }

/** Android: fiziksel klavye cikinca (D2). */
@Serializable
enum class DisconnectAction { SHOW_BAR, SWITCH_PREVIOUS }

@Serializable
data class RadExpandData(
    val format: String = "radexpand",
    val version: Int = 1,
    val shortcuts: List<ShortcutRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    /** Silinmemis kayitlar. */
    val active: List<ShortcutRecord> get() = shortcuts.filter { !it.deleted }

    /** Motor dizini: etkin kayitlar, ayni tetikleyicide EN YENI kayit kazanir. */
    fun buildIndex(): ShortcutIndex =
        ShortcutIndex(active.filter { it.enabled }.sortedByDescending { it.updatedAt }.map { it.toShortcut() })

    val categories: List<String> get() = active.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
}

object DataCodec {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(d: RadExpandData): String = json.encodeToString(RadExpandData.serializer(), d)

    fun decode(s: String): RadExpandData {
        val d = json.decodeFromString(RadExpandData.serializer(), s.removePrefix("﻿"))
        // "medexpand": RadExpand'in eski adiyla (0.1-0.2) alinmis yedekler de okunur.
        require(d.format == "radexpand" || d.format == "medexpand") { "Bu bir RadExpand dosyası değil" }
        return d
    }
}

/** Kayit uzerinde saf islemler (UI ve senkron ayni kurallari kullanir). */
object DataOps {

    fun upsert(d: RadExpandData, r: ShortcutRecord, now: Long): RadExpandData {
        val rec = r.copy(trigger = ShortcutIndex.normalize(r.trigger), updatedAt = now)
        val list = d.shortcuts.toMutableList()
        val i = list.indexOfFirst { it.id == rec.id }
        if (i >= 0) list[i] = rec else list += rec
        return d.copy(shortcuts = list)
    }

    fun delete(d: RadExpandData, ids: Set<String>, now: Long): RadExpandData =
        d.copy(shortcuts = d.shortcuts.map { if (it.id in ids && !it.deleted) it.copy(deleted = true, updatedAt = now) else it })

    fun restore(d: RadExpandData, ids: Set<String>, now: Long): RadExpandData =
        d.copy(shortcuts = d.shortcuts.map { if (it.id in ids && it.deleted) it.copy(deleted = false, updatedAt = now) else it })

    fun setEnabled(d: RadExpandData, ids: Set<String>, enabled: Boolean, now: Long): RadExpandData =
        d.copy(shortcuts = d.shortcuts.map { if (it.id in ids && it.enabled != enabled) it.copy(enabled = enabled, updatedAt = now) else it })

    fun setCategory(d: RadExpandData, ids: Set<String>, category: String, now: Long): RadExpandData =
        d.copy(shortcuts = d.shortcuts.map { if (it.id in ids && it.category != category) it.copy(category = category, updatedAt = now) else it })

    /** [blocking] true ise kayit yapilamaz; degilse yalnizca uyaridir. */
    data class Problem(val blocking: Boolean, val message: String)

    /** Kaydedilmeden once: bos alan, ayni tetikleyici, otomatik kisayollarin birbirini golgelemesi. */
    fun validateDetailed(d: RadExpandData, r: ShortcutRecord): List<Problem> {
        val out = ArrayList<Problem>()
        val t = ShortcutIndex.normalize(r.trigger)
        if (t.isBlank()) out += Problem(true, tx("Kısayol boş olamaz.", "Shortcut cannot be empty."))
        if (t.any { it.isWhitespace() }) out += Problem(true, tx("Kısayol boşluk içeremez.", "Shortcut cannot contain spaces."))
        if (r.replacement.isEmpty()) out += Problem(true, tx("Genişletilecek metin boş.", "Replacement text is empty."))
        val others = d.active.filter { it.id != r.id && it.enabled }
        if (r.enabled) {
            others.firstOrNull { it.trigger == t }?.let {
                out += Problem(true, tx("Aynı kısayol zaten var: «${it.replacement.take(40)}». Önce onu düzenleyin ya da bunu pasif kaydedin.", "This shortcut already exists: «${it.replacement.take(40)}». Edit that one first or save this one as inactive."))
            }
        }
        if (r.mode == ExpandMode.AUTO) {
            // "ab" otomatikse "abc" hic yazilamaz: ab yazilir yazilmaz genisler.
            others.filter { it.trigger.length > t.length && it.trigger.startsWith(t) }.take(3).forEach {
                out += Problem(false, tx("Otomatik olduğu için «${it.trigger}» kısayolu hiç yazılamaz (önce «$t» genişler).", "Because this is automatic, «${it.trigger}» can never be typed («$t» expands first)."))
            }
        }
        others.filter { it.mode == ExpandMode.AUTO && t.length > it.trigger.length && t.startsWith(it.trigger) }.take(3).forEach {
            out += Problem(false, tx("«${it.trigger}» otomatik olduğu için bu kısayol hiç yazılamaz.", "«${it.trigger}» is automatic, so this shortcut can never be typed."))
        }
        if (r.mode != ExpandMode.SPECIAL_ONLY) {
            when (CommonWords.check(t)) {
                CommonWords.Kind.WORD -> out += Problem(false, tx("«$t» günlük dilde bir kelime: Space ile istemeden genişleyebilir. \"Yalnızca Tab\" modunu düşünün.", "«$t» is an everyday word: it may expand by accident with Space. Consider the \"Tab only\" mode."))
                CommonWords.Kind.UNIT -> out += Problem(false, tx("«$t» bir birim/kısaltma (ör. \"500 $t\"): Space ile istemeden genişleyebilir. \"Yalnızca Tab\" modunu düşünün.", "«$t» is a unit/abbreviation (e.g. \"500 $t\"): it may expand by accident with Space. Consider the \"Tab only\" mode."))
                null -> Unit
            }
        }
        if (r.mode == ExpandMode.AUTO && !r.wordBoundary && t.length <= 3) {
            out += Problem(false, tx("Kısa, otomatik ve kelime sınırı kapalı: başka kelimelerin içinde de genişler.", "Short, automatic and without word boundary: it will also expand inside other words."))
        }
        return out
    }

    fun validate(d: RadExpandData, r: ShortcutRecord): List<String> = validateDetailed(d, r).map { it.message }
}

/**
 * Iki cihaz (tablet / Windows) arasinda kisayol birlestirme.
 * Kural: ayni id'de son degistirilen kazanir; silme de bir degisikliktir. Hicbir kayit sessizce kaybolmaz.
 */
object SyncMerge {
    data class Result(val shortcuts: List<ShortcutRecord>, val fromRemote: Int, val fromLocal: Int, val changed: Boolean)

    fun merge(local: List<ShortcutRecord>, remote: List<ShortcutRecord>): Result {
        val byId = LinkedHashMap<String, ShortcutRecord>()
        for (r in local) byId[r.id] = r
        var fromRemote = 0
        var fromLocal = 0
        for (r in remote) {
            val l = byId[r.id]
            when {
                l == null -> {
                    byId[r.id] = r
                    fromRemote++
                }
                r.updatedAt > l.updatedAt -> {
                    if (!r.sameContent(l)) fromRemote++
                    byId[r.id] = r
                }
                l.updatedAt > r.updatedAt && !l.sameContent(r) -> fromLocal++
            }
        }
        val remoteIds = remote.map { it.id }.toSet()
        fromLocal += local.count { it.id !in remoteIds }
        val merged = byId.values.toList()
        return Result(merged, fromRemote, fromLocal, changed = fromRemote > 0)
    }
}
