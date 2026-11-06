package com.radexpand.core

import java.text.Normalizer

/**
 * Kisayollarin hizli arama tablosu. Her tusta bastan olusturulmaz; kisayollar degisince yenisi yapilir.
 */
class ShortcutIndex(shortcuts: List<Shortcut>) {

    /** Etkin kisayollar, tetikleyiciye gore. Ayni tetikleyici birden fazlaysa ilki kullanilir. */
    val byTrigger: Map<String, Shortcut>

    /** Ayni tetikleyiciye sahip (cakisan) kayitlar. */
    val duplicates: List<String>

    val maxTriggerLength: Int

    /** AUTO kisayollarin son karakterleri (buyuk harf bicimleri dahil): her tusta pahali sorgu yapmamak icin on suzgec. */
    val autoLastChars: Set<Char>
    val maxAutoTriggerLength: Int

    init {
        val map = LinkedHashMap<String, Shortcut>()
        val dups = ArrayList<String>()
        for (s in shortcuts) {
            if (!s.enabled) continue
            val t = normalize(s.trigger)
            if (t.isEmpty()) continue
            if (map.containsKey(t)) {
                dups += t
                continue
            }
            map[t] = s.copy(trigger = t, replacement = normalize(s.replacement))
        }
        byTrigger = map
        duplicates = dups
        maxTriggerLength = map.keys.maxOfOrNull { it.length } ?: 0
        val autos = map.values.filter { it.mode == ExpandMode.AUTO }
        autoLastChars = buildSet {
            for (a in autos) {
                val c = a.trigger.last()
                add(c)
                if (a.propagateCase) {
                    TurkishCase.upper(c.toString()).singleOrNull()?.let { add(it) }
                }
            }
        }
        maxAutoTriggerLength = autos.maxOfOrNull { it.trigger.length } ?: 0
    }

    val size: Int get() = byTrigger.size

    companion object {
        /** Turkce karakterlerin tek kod noktasi (NFC) olarak saklanmasini garantiler. */
        fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
    }
}
