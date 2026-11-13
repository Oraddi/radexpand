package com.radexpand.core

import java.time.LocalDateTime
import java.util.Locale

/**
 * Kisayol arama paneli (Alt+Space, Espanso'daki gibi): kayitli kisayollar arasinda arar ve secileni yazdirir.
 * Android ve Windows ayni siralamayi kullanir.
 */
object ShortcutSearch {
    private val TR = Locale.forLanguageTag("tr")

    private fun norm(s: String) = s.lowercase(TR)

    /**
     * [query] bos ise en cok kullanilanlar. Degilse: kisayolu sorguyla BASLAYANLAR once, sonra kisayolda,
     * metinde ya da kategoride GECENLER; her grupta cok kullanilan once.
     */
    fun find(shortcuts: Collection<Shortcut>, query: String, usage: Map<String, Int> = emptyMap(), limit: Int = 50): List<Shortcut> {
        val all = shortcuts.filter { it.enabled }.distinctBy { it.trigger }
        val q = norm(query.trim())
        val byUse = compareByDescending<Shortcut> { usage[it.trigger] ?: 0 }.thenBy { it.trigger.length }.thenBy { it.trigger }
        if (q.isEmpty()) return all.sortedWith(byUse).take(limit)
        val words = q.split(' ').filter { it.isNotEmpty() }
        fun rank(s: Shortcut): Int? {
            val t = norm(s.trigger)
            val hay = t + " " + norm(s.replacement) + " " + norm(s.category)
            if (!words.all { it in hay }) return null
            return when {
                t == q -> 0
                t.startsWith(q) -> 1
                q in t -> 2
                norm(s.replacement).startsWith(q) -> 3
                else -> 4
            }
        }
        return all.mapNotNull { s -> rank(s)?.let { s to it } }
            .sortedWith(compareBy<Pair<Shortcut, Int>> { it.second }.thenComparator { a, b -> byUse.compare(a.first, b.first) })
            .map { it.first }
            .take(limit)
    }

    /** Yazilacak metin ve imlecin sondan kac karakter geri gelecegi ({tarih} gibi degiskenler ve ilk $|$ alani islenir). */
    fun output(s: Shortcut, now: LocalDateTime = LocalDateTime.now()): Pair<String, Int> =
        ExpansionEngine.splitCursor(Variables.expand(s.replacement, now))

    /** Listede gosterilecek tek satirlik onizleme. */
    fun preview(s: Shortcut, max: Int = 80): String {
        val one = s.replacement.replace("\r", "").replace('\n', '⏎').replace(ExpansionEngine.CURSOR, "…")
        return if (one.length > max) one.take(max - 1) + "…" else one
    }
}
