package com.radexpand.app

import com.radexpand.core.undoCombo
import com.radexpand.core.searchCombo
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.radexpand.core.EngineConfig
import com.radexpand.core.ExpansionEngine
import com.radexpand.core.KeyInput
import com.radexpand.core.RadExpandData
import com.radexpand.core.ShortcutIndex

/** Test ekranindaki kutularin privateImeOptions isareti. */
const val TEST_FIELD = "com.radexpand.test"

/** Test ekranindaki serbest alan: kullanicinin gercek kisayollari. */
const val FREE_FIELD = "com.radexpand.free"

/**
 * RadExpand klavye servisi (IME).
 *
 * Fiziksel klavyeden gelen her tus, odaktaki uygulamaya ulasmadan ONCE buraya gelir
 * (Android'in "IME asamasi"). Burada:
 *  - normal harfler aynen uygulamaya birakilir (false dondurulur),
 *  - Space/Enter/Tab geldiginde imlecin solundaki metin okunur; kisayol varsa tus "tuketilir"
 *    ve kisayol InputConnection ile genisletilir.
 * Ekran klavyesi gosterilmez; fiziksel klavye yokken yalnizca kucuk bir bilgi cubugu gorunur.
 */
class RadExpandIme : InputMethodService() {

    private lateinit var engine: ExpansionEngine
    private val port = ImeEditorPort()

    /** Basisi tuketilen tuslarin birakma (up) ve tekrar olaylari da uygulamaya gitmemeli. */
    private val swallowed = HashSet<Int>()
    private var lastDeviceId = Int.MIN_VALUE

    /** Kullanicinin kisayollari ve ayarlari (Repo'dan). Test ekraninda sabit test kisayollari kullanilir. */
    @Volatile private var realIndex: ShortcutIndex = ShortcutIndex(emptyList())
    @Volatile private var realConfig: EngineConfig = EngineConfig()
    @Volatile private var specialCombo: com.radexpand.core.KeyCombo? = null
    @Volatile private var undoCombo: com.radexpand.core.KeyCombo? = null
    @Volatile private var screenCombo: com.radexpand.core.KeyCombo? = null
    @Volatile private var searchCombo: com.radexpand.core.KeyCombo? = null

    /** Alt+Space kisayol arama paneli acik mi. */
    private var searching = false
    private val searchPanel by lazy { SearchPanel(this, ::pickFromSearch) { closeSearch() } }
    private var testField = false

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        CompatStats.init(this)
        SyncManager.init(this)
        engine = ExpansionEngine(realIndex, realConfig, DiagLog)
        engine.observer = CompatStats
        com.radexpand.core.Lang.lang = com.radexpand.core.Lang.defaultFor(Repo.data.value.settings.language)
        applyData(Repo.data.value)
        Repo.addListener(dataListener)
        DiagLog.log("SERVİS başladı (${realIndex.size} kısayol)")
        SyncManager.syncIfStale()
    }

    private val handler by lazy { android.os.Handler(mainLooper) }
    private val dataListener: (RadExpandData) -> Unit = { d -> handler.post { applyData(d) } }

    private fun applyData(d: RadExpandData) {
        realIndex = d.buildIndex()
        realConfig = d.settings.toEngineConfig()
        specialCombo = com.radexpand.core.KeyCombo.parse(d.settings.specialKey)
        undoCombo = d.settings.undoCombo(KeyEvent.KEYCODE_DEL)
        screenCombo = com.radexpand.core.KeyCombo.parse(d.settings.screenKeyboardKey)
        searchCombo = d.settings.searchCombo(KeyEvent.KEYCODE_SPACE)
        DiagLog.enabled = d.settings.diagnosticsEnabled
        if (!testField) {
            engine.index = realIndex
            engine.config = realConfig
        }
    }

    override fun onDestroy() {
        Repo.removeListener(dataListener)
        CompatStats.flush()
        DiagLog.log("SERVİS durdu")
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (attribute == null) return
        // RadExpand'in kendi alanlari:
        //  - serbest deneme alani      -> kullanicinin kisayollari
        //  - test kutulari / WebView   -> sabit test kisayollari (kabul testleri her zaman ayni sonucu versin)
        //  - kisayol duzenleme ekrani  -> genisletme yok (metin yazarken kisayollar acilmasin)
        val own = attribute.packageName == packageName
        val opts = attribute.privateImeOptions ?: ""
        val free = own && FREE_FIELD in opts
        testField = own && !free && (TEST_FIELD in opts || !MainUiState.visible)
        val ownUi = own && !free && !testField
        engine.index = if (testField) PrototypeShortcuts.index else realIndex
        engine.observer = if (testField) com.radexpand.core.EngineObserver.NONE else CompatStats
        engine.config = when {
            testField -> EngineConfig()
            ownUi -> realConfig.copy(enabled = false)
            else -> realConfig
        }
        engine.onStartInput(KeyMapper.fieldInfo(attribute), attribute.initialSelStart, attribute.initialSelEnd)
        SyncManager.syncIfStale()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (searching) closeSearch()
        engine.onFinishInput()
        CompatStats.flush()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        engine.onSelectionChanged(oldSelStart, newSelStart, newSelEnd)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Arama paneli acikken tuslar uygulamaya gitmez (basili tutma tekrarlari dahil).
        if (searching) {
            swallowed += keyCode
            return searchPanel.onKeyDown(event)
        }
        if (event.repeatCount > 0 && keyCode in swallowed) return true
        val ic = currentInputConnection
        if (ic == null || !currentInputStarted) return super.onKeyDown(keyCode, event)

        if (event.deviceId != lastDeviceId) {
            lastDeviceId = event.deviceId
            DiagLog.log("KLAVYE olayı geliyor: ${event.device?.name ?: "bilinmiyor"} (id=${event.deviceId})")
        }
        // Kisayol arama paneli (varsayilan Alt+Space). Parola alaninda ve RadExpand'in kendi ekranlarinda acilmaz.
        if (event.repeatCount == 0 && KeyMapper.isCombo(event, searchCombo) && canSearch()) {
            swallowed += keyCode
            openSearch()
            return true
        }
        // Ekran klavyesine gecis tusu (ayarlardan atanir): Samsung Klavye vb. acilir.
        if (event.repeatCount == 0 && KeyMapper.isCombo(event, screenCombo)) {
            DiagLog.log("TUŞ ekran klavyesine geçiş")
            swallowed += keyCode
            switchToScreenKeyboard()
            return true
        }
        val key = KeyMapper.map(event, specialCombo, undoCombo)
        if (event.repeatCount == 0 && (key == KeyInput.CursorOrEdit || key == KeyInput.Backspace || key is KeyInput.Undo)) {
            DiagLog.log("TUŞ ${KeyMapper.keyName(keyCode)}")
        }
        val consumed = try {
            port.attach(ic, event)
            engine.onKey(key, port)
        } catch (t: Throwable) {
            // Motor hatasi asla yazimi engellememeli: tusu uygulamaya birak.
            DiagLog.log("HATA ${t.javaClass.simpleName}: ${t.message?.take(80)}")
            false
        } finally {
            port.detach()
        }
        if (consumed) {
            swallowed += keyCode
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (swallowed.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** Tam ekran "metin cikarma" modunu hic kullanma. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * Alt seridi yalnizca fiziksel klavye YOKKEN goster. (Varsayilan davranis, sistemde
     * "fiziksel klavye varken ekran klavyesini goster" acikken de gosterirdi; RadExpand'in
     * ekran klavyesi olmadigi icin orada serit sadece yer kaplar.)
     */
    /** D2: fiziksel klavye ayrilinca (ayar aciksa) ekran klavyesine gec. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!hasHardwareKeyboard(newConfig) && autoSwitch()) {
            DiagLog.log("KLAVYE ayrıldı → ekran klavyesine geçiliyor")
            switchToScreenKeyboard()
        }
    }

    /** Yazma alani acildiginda fiziksel klavye yoksa (degisiklik bildirimi kacmis olsa bile) ekran klavyesine gec. */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!hasHardwareKeyboard(resources.configuration) && autoSwitch()) {
            DiagLog.log("KLAVYE yok → ekran klavyesine geçiliyor")
            switchToScreenKeyboard()
        }
    }

    private fun autoSwitch() = Repo.data.value.settings.onKeyboardDisconnect == com.radexpand.core.DisconnectAction.SWITCH_PREVIOUS

    private fun hasHardwareKeyboard(c: Configuration) =
        c.keyboard != Configuration.KEYBOARD_NOKEYS && c.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

    /**
     * Gecilecek ekran klavyesi: once Samsung Klavye, sonra Gboard, yoksa etkin baska bir yazi klavyesi
     * (sesle yazma gibi klavye olmayanlar atlanir).
     */
    private fun screenKeyboardId(): String? {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val list = runCatching { imm.enabledInputMethodList }.getOrNull().orEmpty().filter { it.packageName != packageName }
        fun isKeyboard(i: android.view.inputmethod.InputMethodInfo) =
            !i.id.contains("voice", ignoreCase = true) &&
                (i.subtypeCount == 0 || (0 until i.subtypeCount).any { i.getSubtypeAt(it).mode == "keyboard" })
        return (list.firstOrNull { it.packageName == "com.samsung.android.honeyboard" }
            ?: list.firstOrNull { it.packageName == "com.google.android.inputmethod.latin" }
            ?: list.firstOrNull { isKeyboard(it) })?.id
    }

    private fun switchToScreenKeyboard() {
        val id = screenKeyboardId()
        runCatching {
            when {
                id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> switchInputMethod(id)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> switchToPreviousInputMethod()
                else -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }.onFailure { DiagLog.log("HATA klavye geçişi: ${it.javaClass.simpleName}") }
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        searching || super.onShowInputRequested(flags, configChange)

    override fun onEvaluateInputViewShown(): Boolean {
        if (searching) return true
        val c = resources.configuration
        val hardware = c.keyboard != Configuration.KEYBOARD_NOKEYS &&
            c.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        return !hardware
    }

    // ------------------------------------------------------------------ kisayol arama (Alt+Space)

    private fun canSearch(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (currentInputConnection == null || !engine.config.enabled) return false
        return !KeyMapper.fieldInfo(info).isPassword
    }

    private fun openSearch() {
        searching = true
        DiagLog.log("ARAMA paneli açıldı")
        searchPanel.open(engine.index.byTrigger.values, if (testField) emptyMap() else CompatStats.usage.value)
        setInputView(searchPanel.view)
        updateInputViewShown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) requestShowSelf(0) else showWindow(true)
    }

    private fun closeSearch() {
        if (!searching) return
        searching = false
        setInputView(onCreateInputView())
        updateInputViewShown()
        if (hasHardwareKeyboard(resources.configuration)) requestHideSelf(0)
    }

    private fun pickFromSearch(s: com.radexpand.core.Shortcut) {
        val ic = currentInputConnection
        val pkg = currentInputEditorInfo?.packageName ?: "?"
        closeSearch()
        if (ic == null) return
        val (text, back) = com.radexpand.core.ShortcutSearch.output(s)
        port.attach(ic, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        try {
            port.replaceBeforeCursor(0, text, back)
        } finally {
            port.detach()
        }
        engine.onExternalCursorMove()
        if (!testField) CompatStats.onExpansion(pkg, s.trigger)
        DiagLog.log("ARAMA ile yazıldı '${s.trigger}'")
    }

    /** Fiziksel klavye bagli DEGILKEN gorunen kucuk cubuk. */
    override fun onCreateInputView(): View {
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#263238"))
            setPadding(dp(16), dp(6), dp(8), dp(6))
            addView(TextView(context).apply {
                text = com.radexpand.core.tx("RadExpand · fiziksel klavye bağlı değil", "RadExpand · no physical keyboard")
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = com.radexpand.core.tx("Ekran klavyesi", "On-screen keyboard")
                setOnClickListener { switchToScreenKeyboard() }
            })
            if (!autoSwitch()) {
                addView(Button(context).apply {
                    text = com.radexpand.core.tx("Hep otomatik geç", "Always switch")
                    setOnClickListener {
                        Repo.update { it.copy(settings = it.settings.copy(onKeyboardDisconnect = com.radexpand.core.DisconnectAction.SWITCH_PREVIOUS)) }
                        switchToScreenKeyboard()
                    }
                })
            }
            addView(Button(context).apply {
                text = com.radexpand.core.tx("Klavye seç", "Choose keyboard")
                setOnClickListener { imm.showInputMethodPicker() }
            })
        }
    }
}
