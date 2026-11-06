package com.radexpand.desktop.win

import com.radexpand.core.KeyCombo
import com.radexpand.core.KeyInput
import com.radexpand.core.TriggerKey
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.io.File

/** Sanal tus kodlari (Windows VK_*). */
object Vk {
    const val BACK = 0x08
    const val TAB = 0x09
    const val RETURN = 0x0D
    const val SHIFT = 0x10
    const val CONTROL = 0x11
    const val MENU = 0x12
    const val CAPITAL = 0x14
    const val SPACE = 0x20
    const val LEFT = 0x25
    const val RIGHT = 0x27
    const val LWIN = 0x5B
    const val RWIN = 0x5C
    const val NUMLOCK = 0x90
    const val SCROLL = 0x91
    const val LSHIFT = 0xA0
    const val RSHIFT = 0xA1
    const val LCONTROL = 0xA2
    const val RCONTROL = 0xA3
    const val LMENU = 0xA4
    const val RMENU = 0xA5

    val MODIFIERS = setOf(SHIFT, CONTROL, MENU, CAPITAL, LWIN, RWIN, NUMLOCK, SCROLL, LSHIFT, RSHIFT, LCONTROL, RCONTROL, LMENU, RMENU)
}

/** JNA platform kutuphanesinde olmayan user32 fonksiyonlari. */
interface User32Extra : StdCallLibrary {
    fun GetKeyState(nVirtKey: Int): Short

    companion object {
        val I: User32Extra = Native.load("user32", User32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Fiziksel klavye olayini motorun [KeyInput]'una cevirir (secili klavye duzeni ile: Turkce Q, F...). */
object WinKeys {
    private val u32 = User32.INSTANCE

    private fun down(vk: Int) = (u32.GetAsyncKeyState(vk).toInt() and 0x8000) != 0

    /** Simdi basili degistiriciler ([KeyCombo] bicimi). */
    fun mods(): Int =
        (if (down(Vk.SHIFT)) KeyCombo.SHIFT else 0) or (if (down(Vk.CONTROL)) KeyCombo.CTRL else 0) or
            (if (down(Vk.MENU)) KeyCombo.ALT else 0) or (if (down(Vk.LWIN) || down(Vk.RWIN)) KeyCombo.META else 0)

    fun isCombo(vk: Int, combo: KeyCombo?): Boolean = combo != null && vk == combo.code && mods() == combo.mods

    fun label(c: KeyCombo): String = KeyCombo.modsLabel(c.mods) + keyName(c.code)

    fun keyName(vk: Int): String = when (vk) {
        in 0x70..0x87 -> "F${vk - 0x6F}"
        in 0x30..0x39, in 0x41..0x5A -> vk.toChar().toString()
        Vk.SPACE -> "Space"
        Vk.RETURN -> "Enter"
        Vk.TAB -> "Tab"
        0x2D -> "Insert"
        0x13 -> "Pause"
        0x91 -> "Scroll Lock"
        0x5D -> com.radexpand.core.tx("Menü", "Menu")
        0x24 -> "Home"
        0x23 -> "End"
        else -> com.radexpand.core.tx("Tuş ", "Key ") + "%02X".format(vk)
    }

    fun map(vk: Int, scan: Int): KeyInput {
        if (vk in Vk.MODIFIERS) return KeyInput.Modifier
        val ctrl = down(Vk.CONTROL)
        val alt = down(Vk.MENU)
        val win = down(Vk.LWIN) || down(Vk.RWIN)
        val shift = down(Vk.SHIFT)
        val altGr = ctrl && alt // AltGr = Sol Ctrl + Sag Alt
        when (vk) {
            Vk.SPACE -> return if (ctrl || alt || win) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.SPACE)
            Vk.RETURN -> return if (ctrl || alt || win) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.ENTER)
            Vk.TAB -> return if (ctrl || alt || win || shift) KeyInput.CursorOrEdit else KeyInput.Trigger(TriggerKey.TAB)
            Vk.BACK -> return if (ctrl || alt || win) KeyInput.CursorOrEdit else KeyInput.Backspace
        }
        if (win || ((ctrl || alt) && !altGr)) return KeyInput.CursorOrEdit // Ctrl+V gibi kisayollar
        val ch = toChar(vk, scan, shift, altGr) ?: return KeyInput.CursorOrEdit
        return KeyInput.Typed(ch)
    }

    private fun toChar(vk: Int, scan: Int, shift: Boolean, altGr: Boolean): Char? {
        val state = ByteArray(256)
        if (shift) {
            state[Vk.SHIFT] = 0x80.toByte()
            state[Vk.LSHIFT] = 0x80.toByte()
        }
        if (altGr) {
            state[Vk.CONTROL] = 0x80.toByte()
            state[Vk.LCONTROL] = 0x80.toByte()
            state[Vk.MENU] = 0x80.toByte()
            state[Vk.RMENU] = 0x80.toByte()
        }
        if ((User32Extra.I.GetKeyState(Vk.CAPITAL).toInt() and 1) != 0) state[Vk.CAPITAL] = 0x01
        val buf = CharArray(8)
        // wFlags=4: Windows 10 1607+ cekirdek klavye durumunu (olu tuslar) degistirme
        val n = u32.ToUnicodeEx(vk, scan, state, buf, buf.size, 4, foregroundLayout())
        if (n != 1) return null
        val c = buf[0]
        return if (Character.isISOControl(c)) null else c
    }

    /** On plandaki pencerenin klavye duzeni (her pencerede farkli olabilir). */
    private fun foregroundLayout(): WinDef.HKL? {
        val hwnd = u32.GetForegroundWindow() ?: return u32.GetKeyboardLayout(0)
        val tid = u32.GetWindowThreadProcessId(hwnd, null)
        return u32.GetKeyboardLayout(tid)
    }
}

object WinProcess {
    private val cache = HashMap<Int, String>()

    /** On plandaki pencere ve sahibinin .exe adi (orn. WINWORD.EXE). */
    fun foreground(): Pair<WinDef.HWND?, String> {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null to "?"
        val pid = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid)
        return hwnd to exeName(pid.value)
    }

    fun ownPid(): Int = Kernel32.INSTANCE.GetCurrentProcessId()

    fun pidOf(hwnd: WinDef.HWND?): Int {
        if (hwnd == null) return 0
        val pid = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid)
        return pid.value
    }

    fun exeName(pid: Int): String = synchronized(cache) {
        cache.getOrPut(pid) {
            val h = Kernel32.INSTANCE.OpenProcess(0x1000 /* QUERY_LIMITED_INFORMATION */, false, pid) ?: return@getOrPut "?"
            try {
                val buf = CharArray(1024)
                val len = IntByReference(buf.size)
                if (Kernel32.INSTANCE.QueryFullProcessImageName(h, 0, buf, len)) File(String(buf, 0, len.value)).name else "?"
            } finally {
                Kernel32.INSTANCE.CloseHandle(h)
            }
        }
    }
}

/** RadExpand'in kendi gonderdigi tuslari tanimak icin isaret (dwExtraInfo). */
const val INJECT_MARK = 0x4D58_5041L

/** SendInput ile tus/metin gonderme. */
object Injector {
    private const val KEYUP = 0x0002
    private const val UNICODE = 0x0004
    private const val EXTENDED = 0x0001

    /** [vk, scan, flags] dizileri. [delayMs] > 0 ise her tus (basma+birakma) arasinda bekler. */
    fun send(events: List<IntArray>, delayMs: Int = 0) {
        if (events.isEmpty()) return
        for (chunk in events.chunked(if (delayMs > 0) 2 else 40)) {
            @Suppress("UNCHECKED_CAST")
            val arr = WinUser.INPUT().toArray(chunk.size) as Array<WinUser.INPUT>
            chunk.forEachIndexed { i, e ->
                val inp = arr[i]
                inp.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
                inp.input.setType("ki")
                inp.input.ki.wVk = WinDef.WORD(e[0].toLong())
                inp.input.ki.wScan = WinDef.WORD(e[1].toLong())
                inp.input.ki.dwFlags = WinDef.DWORD(e[2].toLong())
                inp.input.ki.time = WinDef.DWORD(0)
                inp.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(INJECT_MARK)
            }
            User32.INSTANCE.SendInput(WinDef.DWORD(chunk.size.toLong()), arr, arr[0].size())
            Thread.sleep(if (delayMs > 0) delayMs.toLong() else 2L)
        }
    }

    fun tap(vk: Int, extended: Boolean = false): List<IntArray> {
        val f = if (extended) EXTENDED else 0
        return listOf(intArrayOf(vk, 0, f), intArrayOf(vk, 0, f or KEYUP))
    }

    fun text(s: String): List<IntArray> = buildList {
        for (c in s) {
            when (c) {
                '\r' -> Unit
                '\n' -> addAll(tap(Vk.RETURN))
                '\t' -> addAll(tap(Vk.TAB))
                else -> {
                    add(intArrayOf(0, c.code, UNICODE))
                    add(intArrayOf(0, c.code, UNICODE or KEYUP))
                }
            }
        }
    }

    fun backspaces(n: Int): List<IntArray> = (0 until n).flatMap { tap(Vk.BACK) }
    fun lefts(n: Int): List<IntArray> = (0 until n).flatMap { tap(Vk.LEFT, extended = true) }
    fun rights(n: Int): List<IntArray> = (0 until n).flatMap { tap(Vk.RIGHT, extended = true) }

    /**
     * Uzun metni pano ile yapistir (Ctrl+V); panonun onceki metni sonra geri konur.
     * Bazi programlar cok uzun tus dizilerini yavas isledigi icin istege baglidir.
     */
    fun paste(text: String) {
        val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val old = runCatching { cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String }.getOrNull()
        cb.setContents(java.awt.datatransfer.StringSelection(text.replace("\n", "\r\n")), null)
        Thread.sleep(40)
        send(listOf(intArrayOf(Vk.CONTROL, 0, 0), intArrayOf(0x56, 0, 0), intArrayOf(0x56, 0, KEYUP), intArrayOf(Vk.CONTROL, 0, KEYUP)))
        Thread.sleep(300)
        if (old != null) runCatching { cb.setContents(java.awt.datatransfer.StringSelection(old), null) }
    }

    /** Kuyruga alinmis fiziksel bir tusu aynen tekrar gonder. */
    fun replay(vk: Int, scan: Int, up: Boolean, extended: Boolean) {
        var f = if (up) KEYUP else 0
        if (extended) f = f or EXTENDED
        send(listOf(intArrayOf(vk, scan, f)))
    }

    @Suppress("unused")
    private fun ptr(p: Pointer?) = p
}
