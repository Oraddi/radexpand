package com.radexpand.app

import android.app.Activity
import com.radexpand.core.tx
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Asama 1 test ekrani: kurulum durumu, kendini denetleyen kabul testleri, serbest alan, tani kaydi.
 */
class TestActivity : Activity(), InputManager.InputDeviceListener {

    private data class TestCase(
        val title: String,
        val howTo: String,
        val expected: String,
        val singleLine: Boolean = false,
    )

    private class Row(val case: TestCase, val edit: EditText, val result: TextView)

    private val cases = listOf(
        TestCase("1. Yalnızca kısayol", "ri  (başka tuşa basmayın)", "ri"),
        TestCase("2. Space ile", "ri + Space", "hiperintensite "),
        TestCase("3. Enter ile", "ri + Enter", "hiperintensite\n"),
        TestCase("4. Tab ile", "ri + Tab", "hiperintensite"),
        TestCase("5. Tab + ek (bitişik yazım)", "ri + Tab + ler", "hiperintensiteler"),
        TestCase("6. Kelime içinde geçen kısayol", "birinci + Space", "birinci "),
        TestCase("7. Rakamlı kısayol", "t2r + Space", "T2 hiperintens "),
        TestCase("8. Otomatik kısayol", "drimza  (Space'e basmayın)", "Dr. Ayşe ÖZTÜRK"),
        TestCase("9. Türkçe karakterli kısayol", "izş + Space", "izlenmiştir. "),
        TestCase("10. Tek harfli kısayol", "ç + Space", "çapında "),
        TestCase("11. Kelime sonundaki aynı harf", "aç + Space", "aç "),
        TestCase("12. Yalnızca-Tab kısayolu, Space ile", "bel + Space", "bel "),
        TestCase("13. Yalnızca-Tab kısayolu, Tab ile", "bel + Tab", "belirginleşme"),
        TestCase("14. Geri alma", "ri + Space + Shift+Backspace", "ri "),
        TestCase("15. Kısayol + harf", "rix + Space", "rix "),
        TestCase("16. Tek satırlı alanda Enter", "ri + Enter", "hiperintensite", singleLine = true),
        TestCase("17. Hızlı yazım", "olabildiğince hızlı: ri Space ri Space ri Space ri Space ri Space", "hiperintensite ".repeat(5)),
        TestCase("18. Çok satırlı genişletme", "rtü + Space", "Raporda tanımlı bulgular\n\nDr. Ayşe ÖZTÜRK "),
        TestCase("19. Cümle başında büyük harf", "Ri + Space", "Hiperintensite "),
        TestCase("20. İmleç işareti (şablon)", "kcc + Space + 152", "Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür."),
        TestCase("21. Backspace normal siler", "ri + Space + Backspace", "hiperintensite"),
        TestCase("22. Kısayol arama paneli", "Alt+Space, kcc, Enter, 152", "Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür."),
    )

    private val rows = ArrayList<Row>()
    private lateinit var statusView: TextView
    private lateinit var summaryView: TextView
    private lateinit var diagView: TextView
    private lateinit var inputManager: InputManager
    private val handler = Handler(Looper.getMainLooper())
    private var diagPosted = false
    private val diagUpdater = Runnable {
        diagPosted = false
        renderDiag()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(INPUT_SERVICE) as InputManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(text(tx("Test ve tanılama", "Test and diagnostics"), 22f, bold = true))
        root.addView(text(tx("Kabul testleri, aşağıdaki SABİT test kısayollarıyla çalışır (kendi kısayollarınız bu kutuları etkilemez). Serbest deneme alanı ise sizin kısayollarınızı kullanır.", "The acceptance tests use the FIXED test shortcuts below (your own shortcuts do not affect them). The free practice area uses your shortcuts."), 14f, dim = true))

        section(root, tx("1) Kurulum durumu", "1) Setup status"))
        statusView = text("", 15f).also { root.addView(it) }
        root.addView(buttonRow(
            "Klavye ayarlarını aç" to { openSettings(Settings.ACTION_INPUT_METHOD_SETTINGS) },
            "Klavye seç" to { imm().showInputMethodPicker() },
            "Fiziksel klavye ayarları" to { openSettings(Settings.ACTION_HARD_KEYBOARD_SETTINGS) },
        ))

        section(root, tx("2) Kabul testleri", "2) Acceptance tests"))
        root.addView(text(tx("Kutuya dokunun, sonra yalnızca fiziksel klavyeyle yazın. Gösterim: · boşluk, ⏎ yeni satır, ⇥ sekme.", "Tap a box, then type only on the physical keyboard. Shown as: · space, ⏎ new line, ⇥ tab."), 14f, dim = true))
        summaryView = text("", 17f, bold = true).also { root.addView(it) }
        cases.forEach { root.addView(buildRow(it)) }
        root.addView(buttonRow("Tüm test kutularını temizle" to {
            rows.forEach { it.edit.setText("") }
            rows.firstOrNull()?.edit?.requestFocus()
        }))

        section(root, tx("3) Serbest deneme alanı (sizin kısayollarınız)", "3) Free practice area (your shortcuts)"))
        root.addView(EditText(this).apply {
            hint = tx("Kendi kısayollarınızla deneyin, ör.: bir ri odak izş", "Try your own shortcuts here")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            privateImeOptions = FREE_FIELD
        })

        section(root, tx("3b) Chrome motoru (Chromium) testi", "3b) Chrome engine (Chromium) test"))
        root.addView(text("Chrome ve Google Dokümanlar (web) aynı Chromium yazı altyapısını kullanır. Bu sayfa, gerçek Chrome gibi ayrı bir süreçte açılır.", 14f, dim = true))
        root.addView(buttonRow(tx("Chrome motoru test sayfasını aç", "Open the Chrome engine test page") to {
            startActivity(Intent(this, WebTestActivity::class.java))
        }))

        section(root, tx("4) Test kutularındaki sabit kısayollar", "4) Fixed shortcuts used by the test boxes"))
        root.addView(text(PrototypeShortcuts.list.joinToString("\n") { PrototypeShortcuts.describe(it) }, 13f, mono = true))

        section(root, tx("5) Başka uygulamalarda deneme", "5) Trying other apps"))
        root.addView(text(
            "Chrome, Samsung Notes ve Google Dokümanlar'da şunları deneyin: ri+Space, ri+Enter, ri+Tab+ler, birinci+Space. " +
                "Sonra buraya dönüp aşağıdaki kaydı \"Kopyala\" ile kopyalayın ve bana gönderin. " +
                "Kayıtta hangi tuşun RadExpand'e ulaştığı, ulaşmadığı ve editörün metin verip vermediği görünür.",
            14f,
        ))

        section(root, tx("6) Tanılama kaydı (en yeni üstte)", "6) Diagnostic log (newest first)"))
        root.addView(text("Yazdığınız metin kaydedilmez: yalnızca tuş türleri, uygulama adları, kısayol adları ve süreler. Kayıt sadece bellekte durur.", 13f, dim = true))
        root.addView(buttonRow("Kopyala" to { copyDiag() }, "Temizle" to { DiagLog.clear() }))
        diagView = text("", 12f, mono = true).apply { setTextIsSelectable(true) }.also { root.addView(it) }

        updateSummary()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, handler)
        DiagLog.setListener { runOnUiThread { scheduleDiag() } }
        refreshStatus()
        renderDiag()
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        DiagLog.setListener(null)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus() // klavye secici kapandiktan sonra
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshStatus()
    }

    // ------------------------------------------------------------ fiziksel klavye bagla/ayir

    override fun onInputDeviceAdded(deviceId: Int) {
        InputDevice.getDevice(deviceId)?.let { DiagLog.log("CİHAZ bağlandı: ${it.name}") }
        refreshStatus()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        DiagLog.log("CİHAZ ayrıldı (id=$deviceId)")
        refreshStatus()
    }

    override fun onInputDeviceChanged(deviceId: Int) = refreshStatus()

    // ------------------------------------------------------------ durum

    private fun refreshStatus() {
        val enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
        val current = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        } catch (_: Exception) {
            ""
        }
        val selected = current.startsWith("$packageName/")
        val cfg = resources.configuration
        val hw = cfg.keyboard == Configuration.KEYBOARD_QWERTY &&
            cfg.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        val kb = physicalKeyboards()
        statusView.text = buildString {
            appendLine(mark(enabled) + " RadExpand klavyesi etkin" + if (enabled) "" else "  → \"Klavye ayarlarını aç\"")
            appendLine(mark(selected) + " Şu an seçili klavye RadExpand" + if (selected) "" else "  → \"Klavye seç\"")
            appendLine(mark(hw) + " Fiziksel klavye: " + if (kb.isEmpty()) "algılanmadı" else kb.joinToString(", "))
            append("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
    }

    private fun physicalKeyboards(): List<String> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            .map { it.name }

    // ------------------------------------------------------------ testler

    private fun buildRow(c: TestCase): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(2))
        }
        box.addView(text(c.title, 15f, bold = true))
        box.addView(text(tx("Yazın: ", "Type: ") + c.howTo, 14f))
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(this).apply {
            if (c.singleLine) {
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_NEXT
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                maxLines = 6
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            privateImeOptions = TEST_FIELD // klavye bu kutuda sabit test kisayollarini kullanir
        }
        line.addView(edit)
        line.addView(Button(this).apply {
            text = "Temizle"
            isFocusable = false
            setOnClickListener {
                edit.setText("")
                edit.requestFocus()
            }
        })
        box.addView(line)
        val result = text("", 14f).also { box.addView(it) }
        val row = Row(c, edit, result)
        rows += row
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                evaluate(row)
                updateSummary()
            }
        })
        evaluate(row)
        return box
    }

    private fun passed(r: Row) = r.edit.text.toString() == r.case.expected

    private fun evaluate(r: Row) {
        val now = r.edit.text.toString()
        when {
            now == r.case.expected -> {
                r.result.text = "✓ Geçti"
                r.result.setTextColor(GREEN)
                r.result.alpha = 1f
            }
            now.isEmpty() -> {
                r.result.text = "Beklenen: «${vis(r.case.expected)}»"
                r.result.setTextColor(statusView.currentTextColor)
                r.result.alpha = 0.6f
            }
            else -> {
                r.result.text = "Şu an: «${vis(now)}»    Beklenen: «${vis(r.case.expected)}»"
                r.result.setTextColor(ORANGE)
                r.result.alpha = 1f
            }
        }
    }

    private fun updateSummary() {
        val n = rows.count { passed(it) }
        summaryView.text = if (n == cases.size) "✓ Tüm testler geçti ($n / ${cases.size})" else "Geçen testler: $n / ${cases.size}"
        summaryView.setTextColor(if (n == cases.size) GREEN else statusView.currentTextColor)
    }

    private fun vis(s: String) = s.replace(" ", "·").replace("\n", "⏎").replace("\t", "⇥")

    // ------------------------------------------------------------ tani kaydi

    private fun scheduleDiag() {
        if (!diagPosted) {
            diagPosted = true
            handler.postDelayed(diagUpdater, 150)
        }
    }

    private fun renderDiag() {
        diagView.text = DiagLog.snapshot().takeLast(200).asReversed().joinToString("\n")
    }

    private fun copyDiag() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val failed = rows.filter { !passed(it) }.joinToString("\n") {
            "  ✗ ${it.case.title}: «${vis(it.edit.text.toString())}»"
        }
        val text = buildString {
            appendLine("RadExpand $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Fiziksel klavyeler: ${physicalKeyboards()}")
            appendLine("Testler: ${rows.count { passed(it) }} / ${cases.size} geçti")
            if (failed.isNotEmpty()) appendLine(failed)
            appendLine("----")
            append(DiagLog.snapshot().joinToString("\n"))
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("RadExpand tanılama", text))
        Toast.makeText(this, "Kayıt panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ yardimcilar

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false, mono: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            if (mono) typeface = Typeface.MONOSPACE
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (dim) alpha = 0.7f
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun section(root: LinearLayout, title: String) {
        root.addView(text(title, 19f, bold = true).apply { setPadding(0, dp(24), 0, dp(4)) })
    }

    private fun buttonRow(vararg items: Pair<String, () -> Unit>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for ((label, action) in items) {
                addView(Button(context).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { action() }
                })
            }
        }

    companion object {
        private val GREEN = Color.parseColor("#43A047")
        private val ORANGE = Color.parseColor("#EF6C00")
    }
}
