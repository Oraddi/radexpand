package com.radexpand.core

/**
 * Metin genisletme motoru.
 *
 * Temel ilke: Karar verilirken "gercek" metin, editorden (InputConnection) okunan imlec oncesi metindir.
 * Motor ayrica kendi gordugu tuslardan kucuk bir tampon tutar; bu tampon yalnizca
 *  - editor metin veremediginde ya da yavas oldugunda (kor mod) yedek olarak,
 *  - imlec beklenmedik bicimde oynadiginda geri almayi iptal etmek icin,
 *  - tani kaydinda tutarsizlik uyarisi icin
 * kullanilir.
 *
 * Yanlis genisletme, hic genisletmemekten daha kotudur: suphe varsa tus oldugu gibi uygulamaya birakilir.
 */
class ExpansionEngine(
    index: ShortcutIndex,
    config: EngineConfig = EngineConfig(),
    private val diag: DiagSink = DiagSink.NONE,
    private val clock: () -> Long = System::nanoTime,
    /** {tarih}/{saat} degiskenleri icin (testlerde sabitlenir). */
    private val dateTime: () -> java.time.LocalDateTime = { java.time.LocalDateTime.now() },
) {
    @Volatile
    var index: ShortcutIndex = index

    @Volatile
    var config: EngineConfig = config

    @Volatile
    var observer: EngineObserver = EngineObserver.NONE

    private val buf = StringBuilder()
    private var anchored = false
    private var field: FieldInfo? = null
    private var hasSelection = false
    private var last: LastExpansion? = null

    /** Bu alandaki editor metni yavas veriyor (ya da uygulama ayari): artik sorulmaz, tampon kullanilir. */
    private var editorSlow = false

    /** Bizim bildigimiz tus etkilerinin beklenen imlec hareketleri (editor bildirimleriyle eslestirilir). */
    private val pendingMoves = ArrayDeque<Int>()

    private class LastExpansion(val trigger: String, val inserted: String, val restore: String)

    /** Sablondaki sonraki $|$ alanlarina uzakliklar (Tab ile sirayla gidilir). Imlec oynarsa iptal. */
    private val fieldJumps = ArrayDeque<Int>()

    /** Bir eslesme: hangi kisayol, kullanici tam olarak ne yazdi, ne yazilacak (buyuk harf uygulanmis). */
    class Match(val shortcut: Shortcut, val typed: String, val output: String, val style: TurkishCase.Style? = null)

    // ------------------------------------------------------------------ yasam dongusu

    fun onStartInput(field: FieldInfo, selStart: Int, selEnd: Int) {
        this.field = field
        hasSelection = selStart >= 0 && selStart != selEnd
        last = null
        pendingMoves.clear()
        fieldJumps.clear()
        editorSlow = field.packageName in config.blindPackages
        resetBuffer(anchor = selStart == 0 && selEnd == 0)
        val off = when {
            !config.enabled -> " → RadExpand kapalı"
            field.packageName in config.disabledPackages -> " → bu uygulamada kapalı"
            field.isPassword && !config.expandInPasswordFields -> " → parola alanı, genişletme kapalı"
            editorSlow -> " → kör mod (uygulama ayarı)"
            else -> ""
        }
        diag.log("ALAN ${field.packageName} tip=${field.inputTypeHex}${if (field.multiLine) " çok-satır" else ""}${if (field.isNullType) " TYPE_NULL" else ""}$off")
    }

    fun onFinishInput() {
        field = null
        last = null
        pendingMoves.clear()
        resetBuffer(anchor = false)
    }

    /** Editor imleci bildirdiginde cagrilir (InputMethodService.onUpdateSelection). */
    fun onSelectionChanged(oldStart: Int, newStart: Int, newEnd: Int) {
        hasSelection = newStart != newEnd
        if (hasSelection) {
            unknownMove("seçim")
            return
        }
        val actual = newStart - oldStart
        if (actual == 0) return
        var sum = 0
        var k = 0
        for (s in pendingMoves) {
            sum += s
            k++
            if (sum == actual) {
                repeat(k) { pendingMoves.removeFirst() }
                return
            }
        }
        unknownMove("imleç")
    }

    // ------------------------------------------------------------------ tuslar

    /** @return true ise tus tuketildi (uygulamaya gitmemeli). */
    fun onKey(key: KeyInput, port: EditorPort): Boolean {
        val f = field ?: return false
        if (!activeFor(f)) {
            // Parola alaninda / kapali uygulamada hicbir sey tutulmaz.
            resetBuffer(anchor = false)
            last = null
            pendingMoves.clear()
            return false
        }
        return when (key) {
            is KeyInput.Typed -> onTyped(key.ch, f, port)
            is KeyInput.Trigger -> onTrigger(key.key, f, port)
            KeyInput.Backspace -> onBackspace(f, port)
            is KeyInput.Undo -> if (config.undoKey != UndoKey.OFF && tryUndo(f, port)) true else onKey(key.fallback, port)
            KeyInput.CursorOrEdit -> {
                resetBuffer(anchor = false)
                last = null
                pendingMoves.clear()
                fieldJumps.clear()
                false
            }
            KeyInput.Modifier -> false
        }
    }

    private fun activeFor(f: FieldInfo): Boolean {
        val c = config
        return c.enabled && f.packageName !in c.disabledPackages && (!f.isPassword || c.expandInPasswordFields)
    }

    private fun onTyped(ch: Char, f: FieldInfo, port: EditorPort): Boolean {
        last = null
        val idx = index
        val cfg = config
        if (!hasSelection) {
            // Noktalama ile genisletme (istege bagli): "mev," -> "mevcut,"
            if (cfg.punctuationTriggers && ch in cfg.punctuationChars) {
                val m = find(port, f, idx, idx.maxTriggerLength, null, "NOKTALAMA") { it.mode == ExpandMode.MANUAL }
                if (m != null) {
                    val ins = expand(port, f, m, m.typed.length, ch.toString(), "NOKTALAMA")
                    if (ins != null) {
                        if (CURSOR !in m.output) last = LastExpansion(m.shortcut.trigger, ins, m.typed + ch)
                        return true
                    }
                }
            }
            // Otomatik kisayol: son karakter yazilir yazilmaz
            if (ch in idx.autoLastChars) {
                val m = find(port, f, idx, idx.maxAutoTriggerLength - 1, ch, "OTO") { it.mode == ExpandMode.AUTO }
                if (m != null) {
                    // Son karakter henuz editorde degil (tusu biz tuketiyoruz): bir eksik silinir.
                    val ins = expand(port, f, m, m.typed.length - 1, "", "OTO")
                    if (ins != null) {
                        if (CURSOR !in m.output) last = LastExpansion(m.shortcut.trigger, ins, m.typed)
                        return true
                    }
                }
            }
        }
        append(ch)
        addMove(1)
        return false
    }

    private fun onTrigger(key: TriggerKey, f: FieldInfo, port: EditorPort): Boolean {
        last = null
        if (hasSelection) {
            diag.log("$key seçili metin var → geçti")
            return passTrigger(key, f)
        }
        val idx = index
        val cfg = config
        val action = cfg.keyActions[key] ?: KeyAction.OFF
        val m = find(port, f, idx, idx.maxTriggerLength, null, key.name) { sc ->
            when (sc.mode) {
                ExpandMode.MANUAL -> action != KeyAction.OFF && key in cfg.manualKeys
                ExpandMode.SPECIAL_ONLY -> key in cfg.specialKeys
                ExpandMode.AUTO -> false
            }
        }
        if (m == null) {
            // Sablonda sirada bekleyen bir doldurma alani varsa Tab oraya gecer.
            if (key == TriggerKey.TAB && fieldJumps.isNotEmpty()) {
                val jump = fieldJumps.removeFirst()
                port.moveCursor(jump)
                resetBuffer(anchor = false)
                addMove(jump)
                diag.log("TAB sonraki alana geçildi (${fieldJumps.size} alan kaldı)")
                return true
            }
            return passTrigger(key, f)
        }

        val hasCursor = CURSOR in m.output
        val del = m.typed.length
        // "Yalnizca Tab" kisayolunu Tab/ozel tus genisletirse tus kendi isini yapmaz (ek yazmak icin).
        val withKey = action == KeyAction.EXPAND_WITH_KEY && m.shortcut.mode != ExpandMode.SPECIAL_ONLY && !hasCursor
        when {
            key == TriggerKey.SPACE && withKey -> {
                val ins = expand(port, f, m, del, " ", key.name) ?: return passTrigger(key, f)
                last = LastExpansion(m.shortcut.trigger, ins, m.typed + " ")
            }
            (key == TriggerKey.ENTER || key == TriggerKey.TAB) && withKey -> {
                expand(port, f, m, del, "", key.name) ?: return passTrigger(key, f)
                // Tusun kendi isi (yeni satir / sonraki alan) metinden SONRA ayni kanaldan iletilir.
                // Ardindan geri alma yok: yeni satir/gonderim guvenle geri alinamaz.
                port.sendKey(key)
                if (key == TriggerKey.ENTER && f.multiLine) {
                    append('\n')
                    addMove(1)
                } else {
                    resetBuffer(anchor = false)
                }
            }
            else -> {
                // Yalnizca genislet: tusun kendi isi yapilmaz, imlec metnin hemen sonunda kalir.
                val ins = expand(port, f, m, del, "", key.name) ?: return passTrigger(key, f)
                if (!hasCursor) last = LastExpansion(m.shortcut.trigger, ins, m.typed)
            }
        }
        return true
    }

    /** Son genisletmeyi geri al (yalnizca genisletmeden hemen sonra). Basarisizsa false; tus normal islenir. */
    private fun tryUndo(f: FieldInfo, port: EditorPort): Boolean {
        val l = last ?: return false
        if (hasSelection) return false
        val t = if (editorSlow) null else port.textBeforeCursor(l.inserted.length)
        // Editor metin veremiyorsa tampona bak (imlec oynasaydi 'last' zaten silinmis olurdu).
        val same = if (t != null) t.toString() == l.inserted else buf.endsWith(l.inserted)
        if (!same) {
            diag.log("GERİ AL yapılamadı: metin değişmiş ya da okunamadı")
            return false
        }
        if (!port.replaceBeforeCursor(l.inserted.length, l.restore)) return false
        last = null
        bufferReplace(l.inserted.length, l.restore)
        addMove(-l.inserted.length)
        addMove(l.restore.length)
        observer.onUndo(f.packageName, l.trigger)
        diag.log("GERİ AL '${l.trigger}'")
        return true
    }

    private fun onBackspace(f: FieldInfo, port: EditorPort): Boolean {
        if (config.undoKey == UndoKey.BACKSPACE && tryUndo(f, port)) return true
        last = null
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            addMove(-1)
        } else if (!anchored) {
            addMove(-1)
        }
        return false
    }

    // ------------------------------------------------------------------ eslestirme

    private fun find(
        port: EditorPort,
        f: FieldInfo,
        idx: ShortcutIndex,
        ctxLen: Int,
        pending: Char?,
        tag: String,
        accept: (Shortcut) -> Boolean,
    ): Match? {
        if (idx.maxTriggerLength == 0) return null
        val want = ctxLen.coerceAtLeast(0) + 1 // +1: kelime siniri icin soldaki karakter
        val p = pending?.toString() ?: ""
        var ms = 0L
        if (!editorSlow) {
            val t0 = clock()
            val editorText = port.textBeforeCursor(want)
            ms = (clock() - t0) / 1_000_000
            observer.onLookup(f.packageName, ms, editorText != null)
            if (ms >= config.slowEditorMs) {
                editorSlow = true
                diag.log("$tag editör yavaş yanıt verdi (${ms}ms) → bu alanda artık sorulmayacak (kör mod)")
            }
            if (editorText != null) {
                val ctx = editorText.toString() + p
                val m = match(idx, ctx, editorText.length < want, config.words, config.caseMode, config.minUpperLetters, accept)
                diag.log("$tag editör=${editorText.length}kr ${ms}ms → ${describe(m)}${consistencyNote(ctx, m, p)}")
                return m
            }
        }
        if (!config.allowBlindMode) {
            diag.log("$tag editör metni YOK (${ms}ms), kör mod kapalı → geçti")
            return null
        }
        val m = match(idx, buf.toString() + p, anchored, config.words, config.caseMode, config.minUpperLetters, accept)
        diag.log("$tag ${if (editorSlow) "yavaş editör" else "editör metni YOK (${ms}ms)"}, kör mod tampon=${buf.length}kr → ${describe(m)}")
        return m
    }

    private fun describe(m: Match?) = if (m == null) "eşleşme yok" else "'${m.shortcut.trigger}'"

    /** Yalnizca tani amacli: editor metni ile kendi tamponumuz uyusuyor mu? */
    private fun consistencyNote(ctx: String, m: Match?, pending: String): String {
        if (m == null) return ""
        val b = buf.toString() + pending
        val n = minOf(m.typed.length + 1, ctx.length, b.length)
        if (b.length < m.typed.length) return " (tampon kısa)"
        return if (ctx.takeLast(n) == b.takeLast(n)) "" else " ⚠ tampon farklı"
    }

    // ------------------------------------------------------------------ yardimcilar

    /**
     * [del] karakteri silip eslesmenin ciktisini (+ [suffix]) yazar. Imlec isareti varsa
     * suffix eklenmez ve imlec isaretin yerine konur. Yazilan metni dondurur; basarisizsa null.
     */
    private fun expand(port: EditorPort, f: FieldInfo, m: Match, del: Int, suffix: String, tag: String): String? {
        // Degiskenler ({tarih}, {saat}) buyuk harf donusumunden ONCE uygulanir.
        val output = TurkishCase.apply(Variables.expand(m.shortcut.replacement, dateTime()), m.style)
        val fields = splitFields(output)
        val hasCursor = fields.positions.isNotEmpty()
        val text = fields.text
        val back = if (hasCursor) text.length - fields.positions[0] else 0
        val ins = if (hasCursor) text else text + suffix
        if (!port.replaceBeforeCursor(del, ins, back)) {
            diag.log("HATA: editör değişikliği kabul etmedi")
            return null
        }
        bufferReplace(del, ins)
        addMove(-del)
        addMove(ins.length)
        if (back > 0) {
            addMove(-back)
            resetBuffer(anchor = false) // imlec metnin icine girdi: sol tarafi artik bilinmiyor
        }
        // Yeni sablonun alanlari varsa onlar gecerli olur; yoksa (alan icinde baska bir kisayol
        // genisletildiyse) bekleyen alanlar korunur: sonraki alanlar ayni miktarda kaydigi icin uzaklik degismez.
        if (fields.positions.size > 1 || hasCursor) {
            fieldJumps.clear()
            fields.positions.zipWithNext { a, b -> fieldJumps.addLast(b - a) }
        }
        observer.onExpansion(f.packageName, m.shortcut.trigger)
        val caseNote = if (m.typed != m.shortcut.trigger) " (büyük harf)" else ""
        val cursorNote = if (hasCursor) " (imleç işareti)" else ""
        diag.log("$tag '${m.shortcut.trigger}' genişletildi$caseNote$cursorNote")
        return ins
    }

    /** Tetik tusu uygulamaya birakildiginda tamponu guncelle. Her zaman false doner. */
    private fun passTrigger(key: TriggerKey, f: FieldInfo): Boolean {
        when (key) {
            TriggerKey.SPACE -> {
                append(' ')
                addMove(1)
            }
            TriggerKey.ENTER -> if (f.multiLine) {
                append('\n')
                addMove(1)
            } else {
                resetBuffer(anchor = false)
            }
            // Tab: odak degisebilir ya da sekme eklenebilir; ne oldugunu bilemeyiz.
            TriggerKey.TAB, TriggerKey.SPECIAL -> resetBuffer(anchor = false)
        }
        return false
    }

    private fun bufferReplace(del: Int, insert: String) {
        if (buf.length >= del) {
            buf.setLength(buf.length - del)
        } else {
            resetBuffer(anchor = false)
        }
        for (c in insert) append(c)
    }

    private fun append(c: Char) {
        buf.append(c)
        if (buf.length > MAX_BUFFER) {
            buf.delete(0, buf.length - MAX_BUFFER)
            anchored = false
        }
    }

    private fun addMove(d: Int) {
        if (d == 0) return
        pendingMoves.addLast(d)
        if (pendingMoves.size > MAX_PENDING) pendingMoves.clear()
    }

    private fun resetBuffer(anchor: Boolean) {
        buf.setLength(0)
        anchored = anchor || config.unknownStartIsBoundary
    }

    /** Fare tiklamasi vb. ile imlec bilinmeyen bir yere gitti (Windows). */
    fun onExternalCursorMove() = unknownMove("fare")

    private fun unknownMove(reason: String) {
        if (buf.isNotEmpty() || last != null) diag.log("$reason beklenmedik değişti → tampon sıfırlandı")
        resetBuffer(anchor = false)
        last = null
        pendingMoves.clear()
        fieldJumps.clear()
    }

    companion object {
        /** Espanso ile ayni imlec isareti. */
        const val CURSOR = "\$|\$"

        private const val MAX_BUFFER = 256
        private const val MAX_PENDING = 256

        /** Imlec isaretini cikarir; (metin, imlecin sondan geri gidecegi karakter sayisi) dondurur. */
        fun splitCursor(s: String): Pair<String, Int> {
            val f = splitFields(s)
            return if (f.positions.isEmpty()) s to 0 else f.text to (f.text.length - f.positions[0])
        }

        /** Isaretsiz metin + her $|$ isaretinin (isaretsiz metindeki) yeri. */
        class Fields(val text: String, val positions: List<Int>)

        fun splitFields(s: String): Fields {
            val sb = StringBuilder()
            val pos = ArrayList<Int>()
            var i = 0
            while (i < s.length) {
                if (s.startsWith(CURSOR, i)) {
                    pos += sb.length
                    i += CURSOR.length
                } else {
                    sb.append(s[i])
                    i++
                }
            }
            return Fields(sb.toString(), pos)
        }

        /** Varsayilan kelime kurallari (kesme isareti kelimeye dahil: "MR'dan"). */
        fun isWordChar(c: Char): Boolean = WordRules().isWordChar(c)

        /**
         * [ctx] imlec oncesi metindir (sonu imlec). En uzun uyan kisayol secilir.
         * [atFieldStart]: ctx alanin en basindan basliyorsa true (en bastaki kisayol icin sinir saglanmis sayilir).
         * Buyuk harfle yazilmis bir kisayol, propagateCase acikse kucuk harfli karsiligina eslesir.
         */
        fun match(
            idx: ShortcutIndex,
            ctx: String,
            atFieldStart: Boolean,
            words: WordRules = WordRules(),
            caseMode: CaseMode = CaseMode.FULL,
            minUpperLetters: Int = 3,
            accept: (Shortcut) -> Boolean,
        ): Match? {
            val maxLen = minOf(idx.maxTriggerLength, ctx.length)
            for (len in maxLen downTo 1) {
                val start = ctx.length - len
                val typed = ctx.substring(start)
                var sc = idx.byTrigger[typed]
                var style: TurkishCase.Style? = null
                if (sc == null && caseMode != CaseMode.OFF) {
                    val lower = TurkishCase.lower(typed)
                    if (lower != typed) {
                        val cand = idx.byTrigger[lower]
                        if (cand != null && cand.propagateCase) {
                            style = TurkishCase.styleOf(typed)
                            // Tamami buyuk harf ("CC", "ML") radyolojide yaygin kisaltmalardir:
                            // yanlis genislememek icin yalnizca yeterince uzun kisayollarda uygulanir.
                            if (style == TurkishCase.Style.UPPER &&
                                (caseMode != CaseMode.FULL || cand.trigger.count { it.isLetter() } < minUpperLetters)
                            ) style = null
                            if (style != null) sc = cand
                            // "Olduğu gibi": "Ri" de genisler ama metin kayittaki gibi yazilir (Ri -> hiperintens).
                            if (caseMode == CaseMode.KEEP_TEXT) style = null
                        }
                    }
                }
                if (sc == null || !accept(sc)) continue
                if (sc.wordBoundary) {
                    val ok = if (start > 0) !words.isWordChar(ctx[start - 1]) else atFieldStart
                    if (!ok) continue
                }
                return Match(sc, typed, TurkishCase.apply(sc.replacement, style), style)
            }
            return null
        }
    }
}
