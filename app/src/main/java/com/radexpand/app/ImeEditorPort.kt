package com.radexpand.app

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.radexpand.core.EditorPort
import com.radexpand.core.TriggerKey

/** Motorun [EditorPort] arayuzunu Android InputConnection ile uygular. */
class ImeEditorPort : EditorPort {
    private var ic: InputConnection? = null
    private var event: KeyEvent? = null

    fun attach(ic: InputConnection, event: KeyEvent) {
        this.ic = ic
        this.event = event
    }

    fun detach() {
        ic = null
        event = null
    }

    override fun textBeforeCursor(n: Int): CharSequence? = ic?.getTextBeforeCursor(n, 0)

    override fun replaceBeforeCursor(deleteCount: Int, insert: CharSequence, cursorBack: Int): Boolean {
        val c = ic ?: return false
        // Toplu duzenleme: uygulama silme+ekleme arasini ara durum olarak gormez.
        c.beginBatchEdit()
        try {
            if (deleteCount > 0 && !c.deleteSurroundingText(deleteCount, 0)) return false
            if (cursorBack <= 0) return c.commitText(insert, 1)
            // Sablondaki imlec yeri ($|$): metni iki parca yaz. Ikinci parca newCursorPosition=0 ile
            // yazilinca imlec o parcanin BASINDA kalir. Konum hesabi yapilmadigi icin Chrome'un
            // contenteditable alanlarinda da kaymaz.
            val split = insert.length - cursorBack
            if (!c.commitText(insert.subSequence(0, split), 1)) return false
            return c.commitText(insert.subSequence(split, insert.length), 0)
        } finally {
            c.endBatchEdit()
        }
    }

    /**
     * Kullanicinin bastigi Enter / Tab'i, metin degisikliginden SONRA ayni kanal uzerinden iletir.
     * (Tusu dogrudan uygulamaya birakmak sira garantisi vermez.) Shift+Enter gibi degistiriciler korunur.
     */
    override fun sendKey(key: TriggerKey) {
        val c = ic ?: return
        val code = when (key) {
            TriggerKey.ENTER -> KeyEvent.KEYCODE_ENTER
            TriggerKey.TAB -> KeyEvent.KEYCODE_TAB
            else -> return
        }
        val src = event?.takeIf {
            it.keyCode == code || (code == KeyEvent.KEYCODE_ENTER && it.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
        } ?: KeyEvent(KeyEvent.ACTION_DOWN, code)
        c.sendKeyEvent(KeyEvent.changeAction(src, KeyEvent.ACTION_DOWN))
        c.sendKeyEvent(KeyEvent.changeAction(src, KeyEvent.ACTION_UP))
    }

    /** Sablon alanlari arasinda gezinme: ok tusu olaylariyla (her editorde calisir). */
    override fun moveCursor(delta: Int) {
        val c = ic ?: return
        val code = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(delta)) {
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }
}
