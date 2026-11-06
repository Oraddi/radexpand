package com.radexpand.core

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Bir kisayolla ilgili icin rapor satiri. */
data class Issue(val trigger: String, val message: String)

/** Bir Espanso YAML dosyasinin ice aktarma sonucu (henuz veriye eklenmemis). */
data class EspansoReport(
    val fileName: String,
    /** Aktarilabilen kayitlar. */
    val records: List<ShortcutRecord>,
    /** Hic aktarilamayanlar ve nedeni. */
    val unsupported: List<Issue>,
    /** Aktarilan ama dikkat edilmesi gerekenler. */
    val warnings: List<Issue>,
    /** Dosya geneli notlar. */
    val notes: List<String>,
    /** Dosya hic okunamadiysa hata (bu durumda records bostur). */
    val error: String? = null,
)

/**
 * Espanso match dosyasi okuyucu.
 *
 * Esleme (kullanici kararlari, 2026-11-06):
 *  - `word: true`            -> Space/Enter/Tab ile, kelime siniri acik
 *  - (word yok)              -> otomatik (son harfte), kelime siniri ACIK ("anlik ama kelime basinda")
 *  - `left_word: true`       -> otomatik, kelime siniri acik
 *  - `right_word: true`      -> Space/Enter/Tab ile, kelime siniri kapali
 *  - buyuk harf yayma        -> her kayitta acik
 *  - `$|$`                   -> imlec isareti olarak desteklenir
 *  - degiskenler, formlar, resimler, regex, html/markdown -> AKTARILMAZ, nedeni raporlanir
 */
object EspansoImport {

    private val supportedKeys = setOf(
        "trigger", "triggers", "replace", "word", "whole_word", "left_word", "right_word", "propagate_case",
        "label", "search_terms",
    )
    private val unsupportedKeys get() = mapOf(
        "form" to tx("form (doldurulabilir alanlar) desteklenmiyor", "forms are not supported"),
        "form_fields" to tx("form alanları desteklenmiyor", "form fields are not supported"),
        "image_path" to tx("resim genişletme desteklenmiyor", "image expansion is not supported"),
        "html" to tx("HTML biçimli metin desteklenmiyor", "HTML text is not supported"),
        "markdown" to tx("Markdown biçimli metin desteklenmiyor", "Markdown text is not supported"),
        "regex" to tx("düzenli ifade (regex) tetikleyici desteklenmiyor", "regex triggers are not supported"),
    )
    private val droppedKeys get() = mapOf(
        "uppercase_style" to tx("uppercase_style yok sayıldı (RadExpand kendi büyük harf kuralını kullanır)", "uppercase_style ignored (RadExpand uses its own capitalization rule)"),
        "force_clipboard" to tx("force_clipboard yok sayıldı", "force_clipboard ignored"),
        "force_mode" to tx("force_mode yok sayıldı", "force_mode ignored"),
        "paste_shell" to tx("paste_shell yok sayıldı", "paste_shell ignored"),
        "passive_only" to tx("passive_only yok sayıldı", "passive_only ignored"),
    )

    /** Hicbir durumda istisna firlatmaz: beklenmedik bir hata da rapordaki [EspansoReport.error] olur. */
    fun parse(
        fileName: String,
        text: String,
        now: Long = System.currentTimeMillis(),
        noWord: NoWordImport = NoWordImport.AUTO_WORD_START,
    ): EspansoReport =
        try {
            parseUnsafe(fileName, text, now, noWord)
        } catch (e: Exception) {
            EspansoReport(fileName, emptyList(), emptyList(), emptyList(), emptyList(),
                error = tx("Beklenmeyen hata: ", "Unexpected error: ") + "${e.javaClass.simpleName}: ${e.message?.take(200)}")
        }

    private fun parseUnsafe(fileName: String, text: String, now: Long, noWord: NoWordImport): EspansoReport {
        val baseName = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val root: Any? = try {
            Load(LoadSettings.builder().setLabel(fileName).setAllowDuplicateKeys(true).build())
                .loadFromString(text.removePrefix("﻿"))
        } catch (e: Exception) {
            return EspansoReport(fileName, emptyList(), emptyList(), emptyList(), emptyList(),
                error = tx("YAML okunamadı: ", "Could not read YAML: ") + (e.message ?: e.javaClass.simpleName).lines().take(4).joinToString(" "))
        }
        val notes = ArrayList<String>()
        val map = root as? Map<*, *>
            ?: return EspansoReport(fileName, emptyList(), emptyList(), emptyList(), notes, error = tx("Dosyada 'matches:' listesi yok.", "The file has no 'matches:' list."))

        (map["imports"] as? List<*>)?.let { imp ->
            notes += tx("Bu dosya başka dosyaları da içeri alıyor (${imp.joinToString()}). Onları ayrıca içe aktarın.", "This file imports other files (${imp.joinToString()}). Import them separately.")
        }
        if (map.containsKey("global_vars")) notes += tx("global_vars (genel değişkenler) desteklenmiyor; bunları kullanan kısayollar aktarılmadı.", "global_vars are not supported; shortcuts using them were not imported.")
        map.keys.map { it.toString() }.filter { it.startsWith("filter_") }.takeIf { it.isNotEmpty() }?.let {
            notes += tx("Bu dosya yalnızca belirli uygulamalar içindi (${it.joinToString()}); RadExpand'de tüm uygulamalarda çalışır.", "This file was only for certain apps (${it.joinToString()}); in RadExpand it works in all apps.")
        }
        val matches = map["matches"] as? List<*>
        if (matches == null) {
            return EspansoReport(fileName, emptyList(), emptyList(), emptyList(), notes,
                error = if (notes.isEmpty()) tx("Dosyada 'matches:' listesi yok.", "The file has no 'matches:' list.") else null)
        }

        val categories = categoriesFromComments(text, matches.size)
        val records = ArrayList<ShortcutRecord>()
        val unsupported = ArrayList<Issue>()
        val warnings = ArrayList<Issue>()
        val firstByTrigger = HashMap<String, ShortcutRecord>()

        for ((i, item) in matches.withIndex()) {
            val m = item as? Map<*, *>
            if (m == null) {
                unsupported += Issue("#${i + 1}", tx("Geçersiz kayıt (liste öğesi bir eşleşme değil)", "Invalid entry (list item is not a match)"))
                continue
            }
            val triggers = buildList {
                m["trigger"]?.let { add(it.toString()) }
                (m["triggers"] as? List<*>)?.forEach { if (it != null) add(it.toString()) }
            }.map { ShortcutIndex.normalize(it) }
            val label = triggers.firstOrNull() ?: "#${i + 1}"
            if (triggers.isEmpty()) {
                unsupported += Issue(label, if (m.containsKey("regex")) unsupportedKeys.getValue("regex") else tx("Tetikleyici (trigger) yok", "No trigger"))
                continue
            }
            val blocking = m.keys.map { it.toString() }.firstNotNullOfOrNull { unsupportedKeys[it] }
            if (blocking != null) {
                triggers.forEach { unsupported += Issue(it, blocking) }
                continue
            }
            val replaceRaw = m["replace"]
            if (replaceRaw == null) {
                triggers.forEach { unsupported += Issue(it, tx("Genişletilecek metin (replace) yok", "No replacement text")) }
                continue
            }
            val replace = ShortcutIndex.normalize(replaceRaw.toString())
            // Not: Android (ICU) regex'te kapanan suslu parantez de kacirilmalidir; "}}" JVM'de gecer ama Android'de cokertir.
            if (m.containsKey("vars") || VAR_PATTERN.containsMatchIn(replace)) {
                triggers.forEach { unsupported += Issue(it, tx("Değişken ({{...}}) içeriyor; değişkenler henüz desteklenmiyor", "Contains variables ({{...}}), which are not supported yet")) }
                continue
            }
            val itemWarnings = ArrayList<String>()
            m.keys.map { it.toString() }.forEach { k ->
                droppedKeys[k]?.let { itemWarnings += it }
                if (k !in supportedKeys && k !in droppedKeys && k != "vars") itemWarnings += tx("Bilinmeyen alan '$k' yok sayıldı", "Unknown field '$k' ignored")
            }
            val word = truthy(m["word"]) || truthy(m["whole_word"])
            val left = truthy(m["left_word"])
            val right = truthy(m["right_word"])
            val (mode, boundary, note) = when {
                word -> Triple(ExpandMode.MANUAL, true, "")
                right && !left -> Triple(ExpandMode.MANUAL, false, "Espanso: right_word")
                left -> Triple(ExpandMode.AUTO, true, "Espanso: left_word")
                else -> when (noWord) {
                    NoWordImport.AUTO_WORD_START -> Triple(ExpandMode.AUTO, true, tx("Espanso'da anında genişliyordu (word yoktu)", "Expanded instantly in Espanso (no word)"))
                    NoWordImport.AUTO_ANYWHERE -> Triple(ExpandMode.AUTO, false, tx("Espanso'da anında genişliyordu (word yoktu)", "Expanded instantly in Espanso (no word)"))
                    NoWordImport.MANUAL -> Triple(ExpandMode.MANUAL, true, tx("Espanso'da anında genişliyordu (word yoktu); Space/Enter/Tab ile aktarıldı", "Expanded instantly in Espanso (no word); imported as Space/Enter/Tab"))
                }
            }
            if (replace.contains("\"\"")) itemWarnings += tx("Metinde \"\" var: dosyada tırnak hatası olabilir", "Text contains \"\": possible quoting error in the file")
            if (replace.firstOrNull()?.isWhitespace() == true) itemWarnings += tx("Metin boşlukla başlıyor", "Text starts with a space")
            if (replace.trimEnd() != replace) itemWarnings += tx("Metin boşlukla bitiyor", "Text ends with a space")

            for (t in triggers) {
                if (t.isBlank() || t.any { it.isWhitespace() }) {
                    unsupported += Issue(t, tx("Kısayol boş ya da boşluk içeriyor", "Shortcut is empty or contains spaces"))
                    continue
                }
                var rec = ShortcutRecord(
                    trigger = t, replacement = replace, mode = mode, wordBoundary = boundary,
                    propagateCase = true, enabled = true, category = categories.getOrNull(i) ?: baseName,
                    updatedAt = now, note = note,
                )
                val first = firstByTrigger[t]
                if (first != null) {
                    if (first.replacement == rec.replacement && first.mode == rec.mode) {
                        warnings += Issue(t, tx("Dosyada iki kez tanımlı (aynı metin); bir kez aktarıldı", "Defined twice in the file (same text); imported once"))
                        continue
                    }
                    rec = rec.copy(enabled = false, note = tx("Dosyada farklı metinle tekrar ediyor; pasif aktarıldı", "Repeated in the file with different text; imported as inactive"))
                    warnings += Issue(t, tx("Dosyada farklı metinle iki kez tanımlı; ikincisi PASİF aktarıldı", "Defined twice with different text; the second was imported as INACTIVE"))
                } else {
                    firstByTrigger[t] = rec
                }
                itemWarnings.forEach { warnings += Issue(t, it) }
                records += rec
            }
        }
        return EspansoReport(fileName, records, unsupported, warnings, notes)
    }

    private fun truthy(v: Any?) = v == true || v?.toString()?.lowercase() == "true"

    private val VAR_PATTERN = Regex("\\{\\{.*?\\}\\}")
    private val BLOCK_SCALAR = Regex(":\\s*[|>][-+0-9]*\\s*$")

    /**
     * "#KİŞİSEL", "### NÖRO" gibi yorum satirlarini, altindaki kisayollarin kategorisi yapar.
     * Liste ogeleri sayisi tutmazsa (beklenmedik bicim) bos liste doner.
     */
    internal fun categoriesFromComments(text: String, expectedItems: Int): List<String?> {
        val out = ArrayList<String?>()
        var current: String? = null
        var inMatches = false
        var itemIndent = -1
        var inQuoted = false
        var blockIndent = -1
        for (raw in text.removePrefix("﻿").lines()) {
            val line = raw.trimEnd()
            val indent = line.length - line.trimStart().length
            val trimmed = line.trimStart()
            if (blockIndent >= 0) {
                if (trimmed.isEmpty() || indent > blockIndent) continue
                blockIndent = -1
            }
            if (inQuoted) {
                if (oddQuotes(line)) inQuoted = false
                continue
            }
            if (trimmed.startsWith("#")) {
                if (inMatches) {
                    val c = trimmed.trimStart('#').trim()
                    if (c.isNotEmpty()) current = c
                }
                continue
            }
            if (trimmed.isEmpty()) continue
            if (indent == 0 && !trimmed.startsWith("-")) {
                inMatches = trimmed.startsWith("matches:")
                continue
            }
            if (!inMatches) continue
            if (trimmed.startsWith("- ") || trimmed == "-") {
                if (itemIndent < 0) itemIndent = indent
                if (indent == itemIndent) out += current
            }
            if (oddQuotes(line)) inQuoted = true
            if (BLOCK_SCALAR.containsMatchIn(line)) blockIndent = indent
        }
        return if (out.size == expectedItems) out else emptyList()
    }

    private fun oddQuotes(line: String): Boolean {
        var n = 0
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '"') n++
            if (c == '#' && n % 2 == 0 && (i == 0 || line[i - 1].isWhitespace())) break // satir sonu yorumu
            i++
        }
        return n % 2 == 1
    }
}

/** Ice aktarmada mevcut kayitlarla cakisan (ayni kisayol, farkli metin) durumda ne yapilacak. */
@kotlinx.serialization.Serializable
enum class ConflictPolicy { REPLACE, KEEP_EXISTING, KEEP_BOTH }

data class ImportPlan(
    val data: RadExpandData,
    val added: Int,
    val identical: Int,
    val replaced: Int,
    val keptExisting: Int,
    val keptBoth: Int,
    /** (mevcut, gelen) */
    val conflicts: List<Pair<ShortcutRecord, ShortcutRecord>>,
)

object ImportPlanner {
    fun plan(d: RadExpandData, incoming: List<ShortcutRecord>, policy: ConflictPolicy, now: Long): ImportPlan {
        val list = d.shortcuts.toMutableList()
        var added = 0
        var identical = 0
        var replaced = 0
        var keptExisting = 0
        var keptBoth = 0
        val conflicts = ArrayList<Pair<ShortcutRecord, ShortcutRecord>>()
        for (rIn in incoming) {
            val r = rIn.copy(trigger = ShortcutIndex.normalize(rIn.trigger), updatedAt = now)
            val exIdx = list.indexOfFirst { !it.deleted && it.trigger == r.trigger && it.enabled }
                .takeIf { it >= 0 } ?: list.indexOfFirst { !it.deleted && it.trigger == r.trigger }
            if (exIdx < 0) {
                list += r
                added++
                continue
            }
            val ex = list[exIdx]
            if (ex.replacement == r.replacement && ex.mode == r.mode && ex.wordBoundary == r.wordBoundary) {
                identical++
                if (ex.category.isBlank() && r.category.isNotBlank()) list[exIdx] = ex.copy(category = r.category, updatedAt = now)
                continue
            }
            if (!r.enabled) { // dosya ici tekrar: zaten pasif
                list += r
                keptBoth++
                continue
            }
            conflicts += ex to r
            when (policy) {
                ConflictPolicy.REPLACE -> {
                    list[exIdx] = ex.copy(
                        replacement = r.replacement, mode = r.mode, wordBoundary = r.wordBoundary,
                        propagateCase = r.propagateCase, enabled = true,
                        category = r.category.ifBlank { ex.category }, updatedAt = now, note = r.note,
                    )
                    replaced++
                }
                ConflictPolicy.KEEP_EXISTING -> keptExisting++
                ConflictPolicy.KEEP_BOTH -> {
                    list += r.copy(enabled = false, note = tx("İçe aktarmada mevcut kısayolla çakıştı; pasif tutuldu", "Conflicted with an existing shortcut on import; kept inactive"))
                    keptBoth++
                }
            }
        }
        return ImportPlan(d.copy(shortcuts = list), added, identical, replaced, keptExisting, keptBoth, conflicts)
    }
}

/** RadExpand kayitlarini Espanso'nun okuyabilecegi YAML'a cevirir (gecis donemi / yedek icin). */
object EspansoExport {
    fun export(records: List<ShortcutRecord>, now: LocalDateTime = LocalDateTime.now(), includeDisabled: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("# RadExpand: ")
            .append(now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append('\n')
        sb.append("# Kısayolları RadExpand'de düzenleyin / Edit shortcuts in RadExpand; manual edits here are overwritten.\n")
        sb.append("matches:\n")
        val live = records.filter { !it.deleted }
        val byCat = live.groupBy { it.category.ifBlank { "Diğer" } }
        for ((cat, recs) in byCat) {
            sb.append("\n  # ").append(cat.replace("\n", " ")).append('\n')
            for (r in recs) {
                if (!r.enabled) {
                    if (includeDisabled) sb.append("  # (pasif / inactive) ").append(r.trigger).append('\n')
                    continue
                }
                if (r.mode == ExpandMode.SPECIAL_ONLY) sb.append("  # RadExpand'de yalnızca Tab ile genişler\n")
                sb.append("  - trigger: ").append(quote(r.trigger)).append('\n')
                sb.append("    replace: ").append(quote(r.replacement)).append('\n')
                when {
                    r.mode != ExpandMode.AUTO && r.wordBoundary -> sb.append("    word: true\n")
                    r.mode != ExpandMode.AUTO && !r.wordBoundary -> sb.append("    right_word: true\n")
                    r.mode == ExpandMode.AUTO && r.wordBoundary -> sb.append("    left_word: true\n")
                }
                if (r.propagateCase) sb.append("    propagate_case: true\n")
            }
        }
        return sb.toString()
    }

    /** YAML cift tirnakli metin; Turkce karakterler oldugu gibi kalir. */
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\t' -> sb.append("\\t")
                c == '\r' -> sb.append("\\r")
                c < ' ' -> sb.append(String.format("\\x%02X", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
