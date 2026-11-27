package com.radexpand.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.radexpand.core.tx

/**
 * Kelime onerisi cubugu (ekranin altinda ince serit). Fiziksel klavyeyle yazarken gorunur;
 * Tab ilk oneriyi yazar, kelimeye dokunmak da yazar.
 */
class SuggestionBar(private val context: Context, private val onPick: (String) -> Unit) {
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private val slots = List(3) {
        TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    }
    private val hint = TextView(context).apply {
        setTextColor(Color.parseColor("#90A4AE"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        text = tx("RadExpand önerileri", "RadExpand suggestions")
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#263238"))
        addView(hint)
        slots.forEach { addView(it) }
    }

    fun show(words: List<String>) {
        hint.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
        slots.forEachIndexed { i, tv ->
            val w = words.getOrNull(i)
            if (w == null) {
                tv.visibility = View.INVISIBLE
                tv.setOnClickListener(null)
            } else {
                tv.visibility = View.VISIBLE
                tv.text = if (i == 0) "$w  ⇥" else w
                tv.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
                tv.setOnClickListener { onPick(w) }
            }
        }
    }
}
