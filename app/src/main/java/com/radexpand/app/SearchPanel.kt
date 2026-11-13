package com.radexpand.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.radexpand.core.Shortcut
import com.radexpand.core.ShortcutSearch
import com.radexpand.core.tx

/**
 * Kisayol arama paneli (Alt+Space). Fiziksel klavyeden gelen tuslar panel acikken uygulamaya gitmez:
 * harfler aramaya yazilir, ↑/↓ secer, Enter/Tab secileni yazdirir, Esc kapatir. Listeye dokunmak da secer.
 */
class SearchPanel(
    private val context: Context,
    private val onPick: (Shortcut) -> Unit,
    private val onClose: () -> Unit,
) {
    private val query = StringBuilder()
    private var selected = 0
    private var results: List<Shortcut> = emptyList()
    private var source: Collection<Shortcut> = emptyList()
    private var usage: Map<String, Int> = emptyMap()

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private val header = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dp(16), dp(10), dp(16), dp(6))
    }
    private val hint = TextView(context).apply {
        setTextColor(Color.parseColor("#B0BEC5"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(16), 0, dp(16), dp(6))
    }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply {
        addView(list)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260))
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#263238"))
        addView(header)
        addView(hint)
        addView(scroll)
    }

    fun open(shortcuts: Collection<Shortcut>, usage: Map<String, Int>) {
        source = shortcuts
        this.usage = usage
        query.setLength(0)
        selected = 0
        refresh()
    }

    /** Panel acikken her tus buraya gelir. @return her zaman true (tus tuketilir). */
    fun onKeyDown(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> onClose()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB -> results.getOrNull(selected)?.let(onPick) ?: onClose()
            KeyEvent.KEYCODE_DPAD_DOWN -> move(1)
            KeyEvent.KEYCODE_DPAD_UP -> move(-1)
            KeyEvent.KEYCODE_PAGE_DOWN -> move(5)
            KeyEvent.KEYCODE_PAGE_UP -> move(-5)
            KeyEvent.KEYCODE_DEL -> if (query.isNotEmpty()) {
                query.setLength(query.length - 1)
                selected = 0
                refresh()
            }
            else -> {
                if (e.isCtrlPressed || e.isMetaPressed) return true
                val uc = e.getUnicodeChar(e.metaState)
                if (uc > 0 && uc <= 0xFFFF && (uc and KeyCharacterMap.COMBINING_ACCENT) == 0 && !Character.isISOControl(uc.toChar()) && query.length < 60) {
                    query.append(uc.toChar())
                    selected = 0
                    refresh()
                }
            }
        }
        return true
    }

    private fun move(d: Int) {
        if (results.isEmpty()) return
        selected = (selected + d).coerceIn(0, results.size - 1)
        refresh()
        list.getChildAt(selected)?.let { child -> scroll.post { scroll.requestChildFocus(list, child); scroll.smoothScrollTo(0, child.top - dp(40)) } }
    }

    private fun refresh() {
        results = ShortcutSearch.find(source, query.toString(), usage)
        header.text = tx("Kısayol ara: ", "Search shortcuts: ") + query + "▏"
        hint.text = if (results.isEmpty()) tx("Sonuç yok · Esc: kapat", "No results · Esc: close")
        else tx("↑↓ seç · Enter yaz · Esc kapat · ${results.size} sonuç", "↑↓ select · Enter insert · Esc close · ${results.size} results")
        list.removeAllViews()
        results.forEachIndexed { i, s ->
            list.addView(TextView(context).apply {
                text = s.trigger + "   " + ShortcutSearch.preview(s)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setTextColor(Color.WHITE)
                if (i == selected) {
                    setBackgroundColor(Color.parseColor("#00796B"))
                    setTypeface(typeface, Typeface.BOLD)
                }
                setOnClickListener { onPick(s) }
            })
        }
    }
}
