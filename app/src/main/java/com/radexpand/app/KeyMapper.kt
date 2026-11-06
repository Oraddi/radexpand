package com.radexpand.app

import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.radexpand.core.FieldInfo
import com.radexpand.core.KeyCombo
import com.radexpand.core.KeyInput
import com.radexpand.core.TriggerKey

/** Fiziksel klavye olayini motorun anladigi [KeyInput]'a cevirir. */
object KeyMapper {

    /** Olayin degistirici bitleri ([KeyCombo] bicimi). */
    fun mods(e: KeyEvent): Int =
        (if (e.isShiftPressed) KeyCombo.SHIFT else 0) or (if (e.isCtrlPressed) KeyCombo.CTRL else 0) or
            (if (e.isAltPressed) KeyCombo.ALT else 0) or (if (e.isMetaPressed) KeyCombo.META else 0)

    /** Kullanicinin atadigi ozel tus mu? */
    fun isCombo(e: KeyEvent, combo: KeyCombo?): Boolean =
        combo != null && e.keyCode == combo.code && mods(e) == combo.mods

    fun label(e: KeyEvent): String = KeyCombo.modsLabel(mods(e)) + keyName(e.keyCode)

    /** Ozel tus olarak atanamayacak tuslar (yazmayi bozar). */
    fun assignable(e: KeyEvent): Boolean {
        val c = e.keyCode
        if (KeyEvent.isModifierKey(c) || c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_HOME) return false
        val m = mods(e) and KeyCombo.SHIFT.inv()
        val plain = c == KeyEvent.KEYCODE_SPACE || c == KeyEvent.KEYCODE_ENTER || c == KeyEvent.KEYCODE_TAB ||
            c == KeyEvent.KEYCODE_DEL || e.getUnicodeChar(0) != 0
        return !(plain && m == 0)
    }

    fun map(e: KeyEvent, special: KeyCombo? = null): KeyInput {
        val code = e.keyCode
        if (isCombo(e, special)) return KeyInput.Trigger(TriggerKey.SPECIAL)
        if (KeyEvent.isModifierKey(code) ||
            code == KeyEvent.KEYCODE_CAPS_LOCK ||
            code == KeyEvent.KEYCODE_NUM_LOCK ||
            code == KeyEvent.KEYCODE_SCROLL_LOCK ||
            code == KeyEvent.KEYCODE_FUNCTION
        ) {
            return KeyInput.Modifier
        }
        val ctrlOrMeta = e.isCtrlPressed || e.isMetaPressed
        when (code) {
            // Shift+Space ve Shift+Enter tetik sayilir; Ctrl/Alt/Win kombinasyonlari sayilmaz
            // (Ctrl+Space gibi kombinasyonlari sistem dil degistirmek icin kullanir).
            KeyEvent.KEYCODE_SPACE ->
                return if (ctrlOrMeta || e.isAltPressed) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.SPACE)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                return if (ctrlOrMeta || e.isAltPressed) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.ENTER)
            // Shift+Tab: geri gitme (odak) olarak kalsin.
            KeyEvent.KEYCODE_TAB ->
                return if (ctrlOrMeta || e.isAltPressed || e.isShiftPressed) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.TAB)
            KeyEvent.KEYCODE_DEL ->
                return if (ctrlOrMeta || e.isAltPressed) KeyInput.CursorOrEdit else KeyInput.Backspace
        }
        if (ctrlOrMeta) return KeyInput.CursorOrEdit
        // Secili fiziksel klavye duzenine (Turkce Q vb.) gore karakter; AltGr karakterleri dahil.
        val uc = e.getUnicodeChar(e.metaState)
        if (uc > 0 && uc <= 0xFFFF && (uc and KeyCharacterMap.COMBINING_ACCENT) == 0) {
            val ch = uc.toChar()
            if (!Character.isISOControl(ch)) return KeyInput.Typed(ch)
        }
        // Oklar, Home/End, Delete, Esc, F tuslari, olu tuslar (aksan) ...
        return KeyInput.CursorOrEdit
    }

    fun fieldInfo(info: EditorInfo): FieldInfo {
        val t = info.inputType
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        val password = (cls == InputType.TYPE_CLASS_TEXT &&
            (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val multi = cls == InputType.TYPE_CLASS_TEXT && (t and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        return FieldInfo(
            packageName = info.packageName ?: "?",
            isPassword = password,
            isNullType = t == InputType.TYPE_NULL,
            multiLine = multi,
            inputTypeHex = "0x" + Integer.toHexString(t),
        )
    }

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}
