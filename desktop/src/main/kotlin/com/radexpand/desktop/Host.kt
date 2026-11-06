package com.radexpand.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.radexpand.core.DiagSink
import com.radexpand.core.EditorPort
import com.radexpand.core.EngineConfig
import com.radexpand.core.EngineObserver
import com.radexpand.core.ExpansionEngine
import com.radexpand.core.FieldInfo
import com.radexpand.core.KeyCombo
import com.radexpand.core.RadExpandData
import com.radexpand.core.TriggerKey
import com.radexpand.desktop.win.Injector
import com.radexpand.desktop.win.KeyboardHook
import com.radexpand.desktop.win.Vk
import com.radexpand.desktop.win.WinKeys
import com.radexpand.desktop.win.WinProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.io.File
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.radexpand.core.tx

/** Motorun istedigi duzenlemeler; kancanin icinde degil, ayri is parcaciginda uygulanir. */
sealed interface Action {
    data class Replace(val del: Int, val text: String, val back: Int) : Action
    data class Key(val key: TriggerKey) : Action
    data class Move(val delta: Int) : Action
}

/** Windows'ta editorun metni okunamaz: textBeforeCursor her zaman null (motor kendi tamponunu kullanir). */
private class RecordingPort : EditorPort {
    val actions = ArrayList<Action>()
    override fun textBeforeCursor(n: Int): CharSequence? = null
    override fun replaceBeforeCursor(deleteCount: Int, insert: CharSequence, cursorBack: Int): Boolean {
        actions += Action.Replace(deleteCount, insert.toString(), cursorBack)
        return true
    }
    override fun sendKey(key: TriggerKey) {
        actions += Action.Key(key)
    }
    override fun moveCursor(delta: Int) {
        actions += Action.Move(delta)
    }
}

/** Bellekte tani kaydi (diske yazilmaz). Yazilan metin girmez. */
object DesktopDiag : DiagSink {
    /** Ayarlardan kapatilabilir. */
    @Volatile
    var enabled = true
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> get() = _lines

    @Synchronized
    override fun log(event: String) {
        if (!enabled) return
        _lines.value = (_lines.value + (fmt.format(Date()) + "  " + event)).takeLast(400)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

/** Uygulama (.exe) bazinda genisletme / geri alma sayilari + kisayol bazinda kullanim (yalnizca sayi). */
object DesktopStats : EngineObserver {
    data class S(val expansions: Int = 0, val undos: Int = 0)

    private val _m = MutableStateFlow<Map<String, S>>(emptyMap())
    val stats: StateFlow<Map<String, S>> get() = _m

    private val usageSer = MapSerializer(String.serializer(), Int.serializer())
    private val usageFile = File(DesktopStore.dir, "usage.json")
    private val _usage = MutableStateFlow(runCatching { Json.decodeFromString(usageSer, usageFile.readText()) }.getOrDefault(emptyMap()))
    val usage: StateFlow<Map<String, Int>> get() = _usage
    private val saver = Executors.newSingleThreadScheduledExecutor { Thread(it, "radexpand-usage").apply { isDaemon = true } }
    private var pending: ScheduledFuture<*>? = null

    override fun onExpansion(packageName: String, trigger: String) {
        _m.value = _m.value + (packageName to (_m.value[packageName] ?: S()).let { it.copy(expansions = it.expansions + 1) })
        _usage.value = _usage.value + (trigger to (_usage.value[trigger] ?: 0) + 1)
        scheduleSave()
    }

    override fun onUndo(packageName: String, trigger: String) {
        _m.value = _m.value + (packageName to (_m.value[packageName] ?: S()).let { it.copy(undos = it.undos + 1) })
        _usage.value[trigger]?.let { n -> _usage.value = _usage.value + (trigger to maxOf(0, n - 1)) }
        scheduleSave()
    }

    fun resetUsage() {
        _usage.value = emptyMap()
        scheduleSave()
    }

    @Synchronized
    private fun scheduleSave() {
        pending?.cancel(false)
        pending = saver.schedule({ runCatching { usageFile.writeText(Json.encodeToString(usageSer, _usage.value)) } }, 3, TimeUnit.SECONDS)
    }
}

/**
 * Kanca <-> motor <-> SendInput baglantisi.
 *
 * Siralama garantisi: RadExpand metin yazarken (injecting) gelen fiziksel tuslar YUTULUR ve kuyruga
 * alinir; yazma bitince sirayla motordan gecirilip uygulamaya tekrar gonderilir. Boylece hizli yazarken
 * karakter kaybolmaz ve sira karismaz.
 */
object Host {
    private const val OWN_APP = "RadExpand"
    private val lock = Any()
    private lateinit var engine: ExpansionEngine
    private val port = RecordingPort()
    private val swallowedUp = HashSet<Int>()
    private val queue = ArrayDeque<IntArray>() // [vk, scan, flags, down(1/0)]
    private var injecting = false
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "radexpand-inject").apply { isDaemon = true } }
    private var lastHwnd: Any? = null
    private var ownWindow = false
    private val ownPid = WinProcess.ownPid()
    private var baseConfig = EngineConfig()
    private var settings = com.radexpand.core.AppSettings()
    private var specialCombo: KeyCombo? = null
    private var pauseCombo: KeyCombo? = null
    private var hook: KeyboardHook? = null

    /** Ayarlarda "tusa bas" ile tus atanirken: bir sonraki tus buraya gelir (uygulamaya gitmez). */
    @Volatile
    var captureKey: ((KeyCombo, String) -> Unit)? = null

    var paused by mutableStateOf(false)

    /** Test ekrani acikken RadExpand kendi penceresinde de genisletir (duzenleme ekranlarinda genisletmez). */
    @Volatile
    var testScreenActive = false

    private val _recentApps = MutableStateFlow<List<String>>(emptyList())
    val recentApps: StateFlow<List<String>> get() = _recentApps

    val hookActive: Boolean get() = hook?.isActive == true

    fun start(store: DesktopStore) {
        val d = store.data.value
        engine = ExpansionEngine(d.buildIndex(), d.settings.toEngineConfig(), DesktopDiag)
        engine.observer = DesktopStats
        apply(d)
        store.addListener { nd -> synchronized(lock) { apply(nd) } }
        hook = KeyboardHook(::onKey, ::onMouseDown).also { it.start() }
        DesktopDiag.log(tx("RadExpand başladı", "RadExpand started") + " (${d.buildIndex().size})")
    }

    fun stop() = hook?.stop()

    private fun apply(d: RadExpandData) {
        settings = d.settings
        specialCombo = KeyCombo.parse(d.settings.specialKey)
        pauseCombo = KeyCombo.parse(d.settings.pauseHotkey)
        DesktopDiag.enabled = d.settings.diagnosticsEnabled
        baseConfig = d.settings.toEngineConfig()
        engine.index = d.buildIndex()
        engine.config = effectiveConfig()
    }

    private fun effectiveConfig() = if (ownWindow && !testScreenActive) baseConfig.copy(enabled = false) else baseConfig

    // ------------------------------------------------------------------ kanca geri cagrilari

    private fun onKey(vk: Int, scan: Int, flags: Int, down: Boolean, ours: Boolean): Boolean {
        if (ours) return false
        synchronized(lock) {
            if (injecting) {
                queue.addLast(intArrayOf(vk, scan, flags, if (down) 1 else 0))
                return true
            }
            val (consumed, actions) = decide(vk, scan, down)
            if (actions.isNotEmpty()) {
                injecting = true
                worker.execute { runActions(actions) }
            }
            return consumed
        }
    }

    private fun onMouseDown() {
        synchronized(lock) { if (::engine.isInitialized) engine.onExternalCursorMove() }
    }

    /** lock icinde cagrilir. */
    private fun decide(vk: Int, scan: Int, down: Boolean): Pair<Boolean, List<Action>> {
        if (!down) return swallowedUp.remove(vk) to emptyList()
        if (vk in swallowedUp) return true to emptyList() // yutulan tusun basili tutma tekrari
        // Ayarlarda tus atama
        captureKey?.let { cb ->
            if (vk !in Vk.MODIFIERS) {
                captureKey = null
                swallowedUp += vk
                val combo = KeyCombo(vk, WinKeys.mods())
                cb(combo, WinKeys.label(combo))
                return true to emptyList()
            }
        }
        // Genel duraklat / devam kisayolu
        if (WinKeys.isCombo(vk, pauseCombo)) {
            paused = !paused
            swallowedUp += vk
            DesktopDiag.log(if (paused) tx("DURAKLATILDI (kısayol)", "PAUSED (hotkey)") else tx("DEVAM (kısayol)", "RESUMED (hotkey)"))
            return true to emptyList()
        }
        checkForeground()
        if (paused) return false to emptyList()
        val key = if (WinKeys.isCombo(vk, specialCombo)) com.radexpand.core.KeyInput.Trigger(TriggerKey.SPECIAL) else WinKeys.map(vk, scan)
        port.actions.clear()
        val consumed = engine.onKey(key, port)
        if (consumed) swallowedUp += vk
        return consumed to port.actions.toList()
    }

    private fun checkForeground() {
        val (hwnd, exe) = WinProcess.foreground()
        if (hwnd == lastHwnd) {
            val want = effectiveConfig()
            if (engine.config != want) engine.config = want
            return
        }
        lastHwnd = hwnd
        ownWindow = WinProcess.pidOf(hwnd) == ownPid
        val name = if (ownWindow) OWN_APP else exe
        if (!ownWindow && exe != "?") _recentApps.value = (listOf(exe) + _recentApps.value.filter { it != exe }).take(20)
        engine.config = effectiveConfig()
        engine.onStartInput(FieldInfo(name, isPassword = false, isNullType = false, multiLine = true, inputTypeHex = "win"), -1, -1)
    }

    private fun runActions(first: List<Action>) {
        perform(first)
        while (true) {
            val k: IntArray
            val decision: Pair<Boolean, List<Action>>
            synchronized(lock) {
                val n = queue.removeFirstOrNull()
                if (n == null) {
                    injecting = false
                    return
                }
                k = n
                decision = decide(k[0], k[1], k[3] == 1)
            }
            if (decision.first) perform(decision.second)
            else Injector.replay(k[0], k[1], up = k[3] == 0, extended = (k[2] and 0x01) != 0)
        }
    }

    private fun perform(actions: List<Action>) {
        val s = settings
        val delay = s.typingDelayMs.coerceIn(0, 200)
        for (a in actions) {
            when (a) {
                is Action.Replace -> {
                    Injector.send(Injector.backspaces(a.del), delay)
                    if (s.pasteLongerThan > 0 && a.text.length > s.pasteLongerThan) Injector.paste(a.text)
                    else Injector.send(Injector.text(a.text), delay)
                    Injector.send(Injector.lefts(a.back), delay)
                }
                is Action.Key -> Injector.send(Injector.tap(if (a.key == TriggerKey.TAB) Vk.TAB else Vk.RETURN), delay)
                is Action.Move -> Injector.send(if (a.delta > 0) Injector.rights(a.delta) else Injector.lefts(-a.delta), delay)
            }
        }
    }
}
