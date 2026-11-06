package com.radexpand.core

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Genisletmenin NE ZAMAN olacagi.
 * Kelime siniri denetiminden ([Shortcut.wordBoundary]) tamamen bagimsizdir.
 */
@Serializable
enum class ExpandMode {
    /** Space, Enter, Tab (ve ozel tus) ile. Varsayilan. */
    MANUAL,

    /** Kisayolun son karakteri yazilir yazilmaz. */
    AUTO,

    /** Yalnizca ozel tusla (varsayilan: Tab). Gercek kelimeye denk gelen kisayollar icin ("bel", "boy", "on" gibi). */
    SPECIAL_ONLY,
}

/** Motorun calisma anindaki kisayol bicimi. Kalici kayit icin [ShortcutRecord] kullanilir. */
data class Shortcut(
    val trigger: String,
    /** Genisletilecek metin. Icindeki `$|$` imlecin genisletmeden sonra duracagi yerdir (Espanso ile ayni). */
    val replacement: String,
    val mode: ExpandMode = ExpandMode.MANUAL,
    /**
     * Espanso `word: true` karsiligi: kisayolun hemen solunda harf/rakam olmamali.
     * ("birinci" icindeki "ri" genislemez.) Genisletme zamanini belirlemez.
     */
    val wordBoundary: Boolean = true,
    val enabled: Boolean = true,
    val category: String = "",
    /** "Ri" -> "Hiperintens", "RI" -> "HİPERİNTENS" (Turkce kurallarla). */
    val propagateCase: Boolean = true,
)

/** Genisletmeyi tetikleyebilen tuslar. */
enum class TriggerKey { SPACE, ENTER, TAB, SPECIAL }

/** Fiziksel klavye olayinin, motorun anladigi sade hali. Android'e bagimli degildir. */
sealed interface KeyInput {
    /** Yazilabilir bir karakter (harf, rakam, noktalama; Turkce karakterler dahil). */
    data class Typed(val ch: Char) : KeyInput

    data class Trigger(val key: TriggerKey) : KeyInput

    data object Backspace : KeyInput

    /** Imleci veya metni bilinmeyen bicimde degistirebilen tuslar: oklar, Home/End, Delete, Ctrl+V, Esc ... */
    data object CursorOrEdit : KeyInput

    /** Tek basina Shift/Ctrl/Alt/CapsLock gibi: metni degistirmez. */
    data object Modifier : KeyInput
}

/** Odaklanilan metin alaninin ozellikleri. Metnin kendisini icermez. */
data class FieldInfo(
    val packageName: String,
    val isPassword: Boolean,
    /** InputType.TYPE_NULL: editor zengin metin duzenlemeyi desteklemiyor olabilir. */
    val isNullType: Boolean,
    val multiLine: Boolean,
    val inputTypeHex: String = "",
)

data class EngineConfig(
    val enabled: Boolean = true,
    val expandInPasswordFields: Boolean = false,
    /** Genisletmeden hemen sonra Backspace: genisletmeyi geri al, yazilan kisayolu geri getir. */
    val undoWithBackspace: Boolean = true,
    /**
     * Editor imlec oncesi metni veremezse (null donerse), yalnizca kendi gordugumuz tuslara
     * dayanarak genislet. Daha riskli oldugu icin sinir sarti daha kati uygulanir.
     */
    val allowBlindMode: Boolean = true,
    /**
     * Editor imlec oncesi metni bu sureden gec verirse (ms), o alanda bir daha sorulmaz ve
     * kor moda gecilir. Boylece yavas bir editor her Space'te yazimi dondurmez.
     */
    val slowEditorMs: Long = 400,
    /** Her tetik tusunun davranisi (A1). Haritada olmayan tus OFF sayilir. */
    val keyActions: Map<TriggerKey, KeyAction> = mapOf(
        TriggerKey.SPACE to KeyAction.EXPAND_WITH_KEY,
        TriggerKey.ENTER to KeyAction.EXPAND_WITH_KEY,
        TriggerKey.TAB to KeyAction.EXPAND_ONLY,
        TriggerKey.SPECIAL to KeyAction.EXPAND_ONLY,
    ),
    /** Hangi karakterler kelimenin parcasi sayilir (A3). */
    val words: WordRules = WordRules(),
    /** Buyuk harf yayma: kapali / yalnizca cumle basi / tamami buyuk de (A3). */
    val caseMode: CaseMode = CaseMode.FULL,
    /** Tamami buyuk yazim ("NSİ") icin kisayolda en az bu kadar harf olmali ("CC", "ML" korunsun). */
    val minUpperLetters: Int = 3,
    /** MANUAL kisayollari genisleten tuslar. */
    val manualKeys: Set<TriggerKey> = setOf(TriggerKey.SPACE, TriggerKey.ENTER, TriggerKey.TAB, TriggerKey.SPECIAL),
    /** SPECIAL_ONLY kisayollari genisleten tuslar. */
    val specialKeys: Set<TriggerKey> = setOf(TriggerKey.TAB, TriggerKey.SPECIAL),
    /** "mev," -> "mevcut," : noktalama isareti de MANUAL kisayolu genisletsin (isaret arkasina eklenir). */
    val punctuationTriggers: Boolean = false,
    val punctuationChars: String = ".,;:!?",
    /** Bu uygulamalarda RadExpand hic calismaz. */
    val disabledPackages: Set<String> = emptySet(),
    /** Bu uygulamalarda editore metin sorulmaz, dogrudan kor mod kullanilir (yavas/sorunlu editorler). */
    val blindPackages: Set<String> = emptySet(),
    /**
     * Kor modda, imlecin solu bilinmiyorsa (tiklama, pencere degisimi, ok tuslari sonrasi) bunu
     * kelime basi say. Android'de KAPALI (editor metni okunabildigi icin gerek yok). Windows'ta
     * editor metni okunamadigi icin varsayilan ACIK (Espanso ile ayni davranis).
     */
    val unknownStartIsBoundary: Boolean = false,
)

/**
 * Motorun metin alanina eristigi tek kapi. Android tarafinda InputConnection ile,
 * testlerde sahte bir editorle uygulanir.
 */
interface EditorPort {
    /** Imlecin solundaki en fazla [n] karakter; editor desteklemiyorsa null. */
    fun textBeforeCursor(n: Int): CharSequence?

    /**
     * Imlecin solundaki [deleteCount] karakteri sil ve yerine [insert] yaz (tek islem olarak).
     * [cursorBack] > 0 ise imleci yazilan metnin sonundan o kadar geri tasir.
     */
    fun replaceBeforeCursor(deleteCount: Int, insert: CharSequence, cursorBack: Int = 0): Boolean

    /** Kullanicinin bastigi tetik tusunu (Enter ya da Tab) uygulamaya ilet: yeni satir, gonder, sonraki alan. */
    fun sendKey(key: TriggerKey)

    /** Imleci [delta] karakter saga (+) ya da sola (-) tasi (sablon alanlari arasinda gezinme). */
    fun moveCursor(delta: Int)
}

/**
 * Kullanicinin atadigi tus kombinasyonu ("kod:degistiriciler"). Kod platforma ozgudur
 * (Android KeyEvent kodu / Windows VK). Degistiriciler: 1=Shift, 2=Ctrl, 4=Alt, 8=Win/Meta.
 */
data class KeyCombo(val code: Int, val mods: Int) {
    fun encode() = "$code:$mods"

    companion object {
        const val SHIFT = 1
        const val CTRL = 2
        const val ALT = 4
        const val META = 8

        fun parse(s: String?): KeyCombo? {
            val p = s?.split(':') ?: return null
            if (p.size != 2) return null
            return KeyCombo(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null)
        }

        fun modsLabel(mods: Int): String = buildString {
            if (mods and CTRL != 0) append("Ctrl+")
            if (mods and ALT != 0) append("Alt+")
            if (mods and SHIFT != 0) append("Shift+")
            if (mods and META != 0) append("Win+")
        }
    }
}

/** Kisayol metnindeki degiskenler: {tarih} / {date}, {saat} / {time}. */
object Variables {
    private val DATE = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    val names = listOf("{tarih}", "{date}", "{saat}", "{time}")

    fun expand(s: String, now: java.time.LocalDateTime): String {
        if ('{' !in s) return s
        val d = now.format(DATE)
        val t = now.format(TIME)
        return s.replace("{tarih}", d).replace("{date}", d).replace("{saat}", t).replace("{time}", t)
    }
}

/**
 * Kelimenin parcasi sayilan karakterler. Harf, rakam ve alt cizgi her zaman kelimedir.
 * Kesme isareti varsayilan olarak kelimeye dahildir: Turkcede ek kesmeden sonra gelir ("MR'dan").
 */
data class WordRules(
    val apostropheIsWord: Boolean = true,
    val hyphenIsWord: Boolean = false,
    val slashIsWord: Boolean = false,
) {
    fun isWordChar(c: Char): Boolean =
        Character.isLetterOrDigit(c) || c == '_' ||
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
            (apostropheIsWord && (c == '\'' || c == '’')) ||
            (hyphenIsWord && c == '-') ||
            (slashIsWord && c == '/')
}

/** Tani kaydi. Kullanicinin yazdigi metin ASLA buraya gonderilmez; yalnizca olay turleri ve kisayol adlari. */
fun interface DiagSink {
    fun log(event: String)

    companion object {
        val NONE = DiagSink { }
    }
}

/** Uygulama bazinda uyumluluk istatistigi icin olaylar (metin icermez). */
interface EngineObserver {
    fun onLookup(packageName: String, millis: Long, editorGaveText: Boolean) {}
    fun onExpansion(packageName: String, trigger: String) {}
    fun onUndo(packageName: String, trigger: String) {}

    companion object {
        val NONE = object : EngineObserver {}
    }
}

/** Turkce buyuk/kucuk harf kurallari (i/İ, ı/I). */
object TurkishCase {
    val TR: Locale = Locale.forLanguageTag("tr")

    enum class Style { CAPITAL, UPPER }

    fun lower(s: String) = s.lowercase(TR)
    fun upper(s: String) = s.uppercase(TR)

    /** Yazilan kisayolun buyuk harf bicimi; karisik ("rI") ise null. */
    fun styleOf(typed: String): Style? {
        val letters = typed.filter { it.isLetter() }
        if (letters.isEmpty()) return null
        if (letters.all { it.isUpperCase() }) return if (letters.length >= 2) Style.UPPER else Style.CAPITAL
        if (letters.first().isUpperCase() && letters.drop(1).all { it.isLowerCase() }) return Style.CAPITAL
        return null
    }

    fun apply(text: String, style: Style?): String = when (style) {
        null -> text
        Style.UPPER -> upper(text)
        Style.CAPITAL -> {
            val i = text.indexOfFirst { it.isLetter() }
            if (i < 0) text else text.substring(0, i) + upper(text[i].toString()) + text.substring(i + 1)
        }
    }
}
