package com.radexpand.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Chromium (Chrome / Google Dokumanlar web ile ayni yazi altyapisi) test sayfasi.
 *
 * Bilerek AYRI bir surecte (":web") calisir: klavye servisi ile ayni surecte olursa iki taraf
 * birbirini bekler ve gercekte olmayan 2 saniyelik gecikme gorulur. Ayri surecte, gercek
 * Chrome'daki durumu taklit eder. Yalnizca yerel HTML; internet kullanilmaz.
 */
class WebTestActivity : Activity() {

    private lateinit var out: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("web")
            } catch (_: IllegalStateException) {
            }
        }
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "Chrome motoru (Chromium) testi"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "İki kutuda da deneyin: ri+Space, ri+Tab+ler, ri+Enter. Altta sonuç görünür (· boşluk, ⏎ yeni satır)."
            textSize = 14f
            alpha = 0.7f
        })
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="light dark">
            <style>
              body{font:18px sans-serif;margin:4px}
              textarea,div[contenteditable]{display:block;width:100%;box-sizing:border-box;min-height:90px;
                border:1px solid #888;border-radius:6px;padding:8px;margin:4px 0 14px;font:18px sans-serif}
            </style></head><body>
            <b>textarea</b><textarea id="t"></textarea>
            <b>contenteditable</b><div id="c" contenteditable="true"></div>
            <script>
              function vis(s){return s.replace(/ /g,'·').replace(/ /g,'·').replace(/\n/g,'⏎').replace(/\t/g,'⇥');}
              function upd(){RadExpand.report('textarea: «'+vis(t.value)+'»\ncontenteditable: «'+vis(c.innerText)+'»');}
              t.addEventListener('input',upd);c.addEventListener('input',upd);upd();
            </script></body></html>
        """.trimIndent()
        root.addView(WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(Report(), "RadExpand")
            loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330))
        })
        out = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
        }
        root.addView(out)
        setContentView(root)
    }

    private inner class Report {
        @JavascriptInterface
        fun report(s: String) {
            runOnUiThread { out.text = s }
        }
    }
}
