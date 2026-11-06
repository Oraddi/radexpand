package com.radexpand.core

import kotlin.math.max

/**
 * Sahte metin editoru: Android'deki bir EditText'in yaptigini taklit eder.
 * [supportsText] false ise, imlec oncesi metni veremeyen "zor" bir editoru taklit eder.
 */
open class FakeEditor(
    var supportsText: Boolean = true,
    val multiLine: Boolean = true,
    /** Tab tusu islenmezse: true -> sekme karakteri ekler, false -> odagi baska alana tasir (metin degismez). */
    val tabInsertsTab: Boolean = true,
) : EditorPort {
    val text = StringBuilder()
    var cursor = 0
    var editorActions = 0
    var icCalls = 0

    override fun textBeforeCursor(n: Int): CharSequence? {
        icCalls++
        if (!supportsText) return null
        return text.substring(max(0, cursor - n), cursor)
    }

    override fun replaceBeforeCursor(deleteCount: Int, insert: CharSequence, cursorBack: Int): Boolean {
        val s = max(0, cursor - deleteCount)
        text.replace(s, cursor, insert.toString())
        cursor = s + insert.length - cursorBack
        return true
    }

    override fun sendKey(key: TriggerKey) {
        when (key) {
            TriggerKey.ENTER -> enter()
            TriggerKey.TAB -> { tabsSent++; if (tabInsertsTab) insert("\t") }
            else -> Unit
        }
    }

    var tabsSent = 0

    override fun moveCursor(delta: Int) {
        cursor = (cursor + delta).coerceIn(0, text.length)
    }

    fun insert(s: String) {
        text.insert(cursor, s)
        cursor += s.length
    }

    fun enter() {
        if (multiLine) insert("\n") else editorActions++
    }

    fun backspace() {
        if (cursor > 0) {
            text.deleteCharAt(cursor - 1)
            cursor--
        }
    }

    override fun toString() = text.toString()
}

/**
 * Klavye + uygulama simulasyonu. Gercek Android'deki sira ile calisir:
 * tus once motora (IME) gider; tuketilmezse uygulama kendi davranisini uygular;
 * sonra editor imlec degisikligini IME'ye bildirir.
 *
 * Yazim kisaltmalari: ␣ = Space, ⏎ = Enter, ⇥ = Tab, ⌫ = Backspace, ← = sol ok
 */
class Sim(
    shortcuts: List<Shortcut>,
    val editor: FakeEditor = FakeEditor(),
    config: EngineConfig = EngineConfig(),
    val field: FieldInfo = FieldInfo("test.app", isPassword = false, isNullType = false, multiLine = editor.multiLine),
    /** false: editor imlec bildirimlerini geciktirir (Chrome gibi asenkron editorleri taklit) */
    private val reportSelectionImmediately: Boolean = true,
    clock: () -> Long = System::nanoTime,
    dateTime: () -> java.time.LocalDateTime = { java.time.LocalDateTime.of(2026, 11, 6, 9, 5) },
) {
    val log = ArrayList<String>()
    val engine = ExpansionEngine(ShortcutIndex(shortcuts), config, { log += it }, clock, dateTime)
    private var lastReported = editor.cursor

    init {
        engine.onStartInput(field, editor.cursor, editor.cursor)
    }

    fun type(seq: String): Sim {
        for (c in seq) {
            when (c) {
                '␣' -> press(KeyInput.Trigger(TriggerKey.SPACE))
                '⏎' -> press(KeyInput.Trigger(TriggerKey.ENTER))
                '⇥' -> press(KeyInput.Trigger(TriggerKey.TAB))
                '⌫' -> press(KeyInput.Backspace)
                '←' -> press(KeyInput.CursorOrEdit)
                '★' -> press(KeyInput.Trigger(TriggerKey.SPECIAL))
                else -> press(KeyInput.Typed(c))
            }
        }
        return this
    }

    fun press(key: KeyInput) {
        val consumed = engine.onKey(key, editor)
        if (!consumed) {
            when (key) {
                is KeyInput.Typed -> editor.insert(key.ch.toString())
                is KeyInput.Trigger -> when (key.key) {
                    TriggerKey.SPACE -> editor.insert(" ")
                    TriggerKey.ENTER -> editor.enter()
                    TriggerKey.TAB -> if (editor.tabInsertsTab) editor.insert("\t")
                    TriggerKey.SPECIAL -> Unit
                }
                KeyInput.Backspace -> editor.backspace()
                KeyInput.CursorOrEdit -> if (editor.cursor > 0) editor.cursor-- // sol ok
                KeyInput.Modifier -> Unit
            }
        }
        if (reportSelectionImmediately) flushSelection()
    }

    /** Editorun bekleyen imlec bildirimlerini gonder. */
    fun flushSelection() {
        if (editor.cursor != lastReported) {
            engine.onSelectionChanged(lastReported, editor.cursor, editor.cursor)
            lastReported = editor.cursor
        }
    }

    /** Kullanici fare/dokunmatikle imleci baska yere tasidi. */
    fun clickAt(pos: Int) {
        editor.cursor = pos
        flushSelection()
    }

    val text: String get() = editor.toString()
}
