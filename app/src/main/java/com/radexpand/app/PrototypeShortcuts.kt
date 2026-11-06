package com.radexpand.app

import com.radexpand.core.ExpandMode
import com.radexpand.core.Shortcut
import com.radexpand.core.ShortcutIndex

/**
 * Asama 1 icin sabit test kisayollari. Asama 2'de yerini duzenlenebilir kisayol listesi alacak,
 * Asama 3'te Espanso base.yml aktarimi gelecek.
 */
object PrototypeShortcuts {
    val list = listOf(
        Shortcut("ri", "hiperintensite"),
        Shortcut("oi", "hipointens"),
        Shortcut("t2r", "T2 hiperintens"),
        Shortcut("t2o", "T2 hipointens"),
        Shortcut("iz", "izlendi."),
        Shortcut("izş", "izlenmiştir."),
        Shortcut("izk", "izlenmektedir."),
        Shortcut("ç", "çapında"),
        Shortcut("nsi", "Normal sınırlarda inceleme"),
        Shortcut("rtü", "Raporda tanımlı bulgular\n\nDr. Ayşe ÖZTÜRK"),
        Shortcut("bel", "belirginleşme", mode = ExpandMode.SPECIAL_ONLY),
        Shortcut("drimza", "Dr. Ayşe ÖZTÜRK", mode = ExpandMode.AUTO),
        Shortcut("ivd", "intervertebral disk", mode = ExpandMode.AUTO, wordBoundary = false),
        Shortcut("kcc", "Karaciğer kraniokaudal boyutu \$|\$ mm ölçülmüştür."),
    )

    val index = ShortcutIndex(list)

    fun describe(s: Shortcut): String {
        val mode = when (s.mode) {
            ExpandMode.MANUAL -> "Space/Enter/Tab"
            ExpandMode.AUTO -> "otomatik"
            ExpandMode.SPECIAL_ONLY -> "yalnızca Tab"
        }
        val word = if (s.wordBoundary) "kelime sınırı açık" else "kelime sınırı KAPALI"
        val repl = s.replacement.replace("\n", "⏎")
        return "${s.trigger}  →  $repl     [$mode · $word]"
    }
}
