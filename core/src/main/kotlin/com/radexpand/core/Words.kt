package com.radexpand.core

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.TreeMap

/** Ogrenilmis kelime: [w] kucuk harf, [n] kac kez yazildi, [t] son yazilma zamani (ms). */
@Serializable
data class LearnedWord(val w: String, val n: Int = 1, val t: Long = 0)

private val TR: Locale = Locale.forLanguageTag("tr")

internal fun lowerTr(s: String) = s.lowercase(TR)

/**
 * Kullanicinin yazdigi kelimeleri ogrenir (yalnizca cihazda ve kullanicinin kendi senkron dosyasinda durur).
 * Sadece harflerden olusan 4-30 harfli kelimeler; parola alanlarinda ve kapali uygulamalarda motor cagirmaz.
 */
class WordLearner(private val maxWords: Int = 20_000) {
    private val map = TreeMap<String, LearnedWord>()

    /** Son "ogrenilenleri sil" zamani; bundan eski kayitlar senkronda geri gelmez. */
    @Volatile
    var clearedAt: Long = 0
        private set

    /** Kaydedilmemis degisiklik var mi. */
    @Volatile
    var dirty = false
        private set

    val size: Int @Synchronized get() = map.size

    /** Kayitli (ya da senkrondan gelen) listeyi birlestir. */
    @Synchronized
    fun mergeFrom(words: List<LearnedWord>, cleared: Long) {
        if (cleared > clearedAt) {
            clearedAt = cleared
            map.values.removeIf { it.t < cleared }
        }
        for (r in words) {
            if (r.t < clearedAt) continue
            val k = lowerTr(r.w)
            val l = map[k]
            map[k] = if (l == null) r.copy(w = k) else LearnedWord(k, maxOf(l.n, r.n), maxOf(l.t, r.t))
        }
    }

    @Synchronized
    fun learn(word: String, now: Long) {
        if (word.length !in 4..30 || !word.all { it.isLetter() }) return
        val k = lowerTr(word)
        val l = map[k]
        map[k] = LearnedWord(k, (l?.n ?: 0) + 1, now)
        dirty = true
        if (map.size > maxWords) {
            // En az kullanilan ve en eski kelimeleri at
            map.values.sortedWith(compareBy<LearnedWord> { it.n }.thenBy { it.t }).take(map.size - maxWords).forEach { map.remove(it.w) }
        }
    }

    @Synchronized
    fun clear(now: Long) {
        map.clear()
        clearedAt = now
        dirty = true
    }

    /** [prefix] ile baslayan kelimeler (kucuk harf). */
    @Synchronized
    fun withPrefix(prefix: String, limit: Int = 50): List<LearnedWord> {
        val out = ArrayList<LearnedWord>()
        for ((k, v) in map.tailMap(prefix, true)) {
            if (!k.startsWith(prefix)) break
            out += v
            if (out.size >= limit) break
        }
        return out
    }

    /** Kaydetmek icin anlik liste; [dirty] temizlenir. */
    @Synchronized
    fun snapshot(): List<LearnedWord> {
        dirty = false
        return map.values.toList()
    }
}

/** Uygulama icindeki hazir kelime listeleri (core/src/main/resources/dict). */
object WordLists {
    private fun load(name: String): List<String> = runCatching {
        WordLists::class.java.getResourceAsStream("/dict/$name")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { lowerTr(it) }.toList()
        }
    }.getOrNull() ?: emptyList()

    /** Turkce genel liste, siklik sirasinda (FrequencyWords, CC BY-SA 4.0). */
    val turkishGeneral: List<String> by lazy { load("tr_general.txt") }
    val turkishMedical: List<String> by lazy { load("tr_medical.txt") }
    val englishMedical: List<String> by lazy { load("en_medical.txt") }
}

/** Siralanmis bir kelime listesinde onekle arama (ikili arama). */
internal class PrefixList(words: List<String>) {
    /** Kelime -> siklik sirasi (0 = en sik). */
    private val rank = HashMap<String, Int>(words.size * 2)
    private val sorted: Array<String>

    init {
        words.forEachIndexed { i, w -> rank.putIfAbsent(w, i) }
        sorted = rank.keys.toTypedArray().also { it.sort() }
    }

    val size get() = sorted.size

    fun rankOf(w: String) = rank[w] ?: Int.MAX_VALUE

    fun withPrefix(prefix: String, limit: Int = 400): List<String> {
        var i = sorted.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        val out = ArrayList<String>()
        while (i < sorted.size && sorted[i].startsWith(prefix) && out.size < limit) out += sorted[i++]
        return out
    }
}

/**
 * Kelime onerisi ("mut" -> "mutfak"). Kaynaklar ve agirliklari:
 * ogrenilen kelimeler (en az 2 kez yazilmis) > tip/radyoloji terimleri > kisayol metinlerindeki kelimeler >
 * Turkce genel liste (siklik sirasina gore).
 */
class Suggester(
    general: List<String>,
    medical: List<String>,
    shortcutWords: Collection<String>,
    private val learner: WordLearner?,
) {
    private val general = PrefixList(general)
    private val medical = PrefixList(medical)
    private val fromShortcuts = PrefixList(shortcutWords.map { lowerTr(it) }.filter { it.length >= 4 && it.all(Char::isLetter) })

    /**
     * [typed]: imlecten onceki yarim kelime (ornegin "Mut"). Donus: buyuk/kucuk harfi [typed]'a uydurulmus
     * en fazla [max] oneri; her biri en az 2 harf ekler.
     */
    fun suggest(typed: String, max: Int = 3): List<String> {
        val p = lowerTr(typed)
        if (p.isEmpty()) return emptyList()
        val score = HashMap<String, Double>()
        fun add(w: String, s: Double) {
            if (w.length < p.length + 2) return
            if ((score[w] ?: -1.0) < s) score[w] = s
        }
        learner?.withPrefix(p)?.forEach { lw -> if (lw.n >= 2) add(lw.w, 10_000.0 + minOf(lw.n, 500) * 10.0) }
        medical.withPrefix(p).forEach { add(it, 5_000.0) }
        fromShortcuts.withPrefix(p).forEach { add(it, 4_000.0) }
        if (general.size > 0) general.withPrefix(p).forEach { w -> add(w, 3_000.0 * (1.0 - general.rankOf(w).toDouble() / general.size)) }
        // Tek kez yazilmis ogrenilen kelime: yalnizca baska kaynak da biliyorsa (yazim hatasi onerilmesin)
        learner?.withPrefix(p)?.forEach { lw -> if (lw.n == 1 && score.containsKey(lw.w)) score[lw.w] = score[lw.w]!! + 1_000.0 }
        val style = TurkishCase.styleOf(typed)
        return score.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key.length })
            .take(max)
            .map { TurkishCase.apply(it.key, style) }
    }

    companion object {
        /** Kisayol metinlerindeki kelimeler (oneri kaynagi). */
        fun wordsOf(texts: Collection<String>): Set<String> =
            texts.flatMap { t -> t.split(Regex("[^\\p{L}]+")) }.filter { it.length >= 4 }.map { lowerTr(it) }.toSet()
    }
}

/** Ogrenilen kelimelerin cihazlar arasi birlestirilmesi. */
object WordMerge {
    data class Result(val words: List<LearnedWord>, val clearedAt: Long, val changed: Boolean)

    fun merge(local: List<LearnedWord>, localCleared: Long, remote: List<LearnedWord>, remoteCleared: Long): Result {
        val l = WordLearner(Int.MAX_VALUE)
        l.mergeFrom(local, localCleared)
        val before = l.snapshot().toSet()
        l.mergeFrom(remote, remoteCleared)
        val after = l.snapshot()
        return Result(after, maxOf(localCleared, remoteCleared), after.toSet() != before || remoteCleared > localCleared)
    }
}
