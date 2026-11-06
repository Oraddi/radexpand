package com.radexpand.desktop.win

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/** Fare olay geri cagrisi (JNA platform'da hazir tanimi yok). */
interface LowLevelMouseProc : WinUser.HOOKPROC {
    fun callback(nCode: Int, wParam: WinDef.WPARAM, lParam: Pointer): WinDef.LRESULT
}

/**
 * Windows genel klavye/fare kancasi (WH_KEYBOARD_LL / WH_MOUSE_LL). Kendi mesaj dongusu olan bir
 * is parcaciginda calisir. Geri cagri hizli donmelidir (Windows ~300 ms'yi asan kancayi kaldirir).
 */
class KeyboardHook(
    /** true donerse tus yutulur (uygulamaya gitmez). */
    private val onKey: (vk: Int, scan: Int, flags: Int, down: Boolean, injectedByUs: Boolean) -> Boolean,
    private val onMouseDown: () -> Unit,
) {
    private val u32 = User32.INSTANCE
    private var kbHook: WinUser.HHOOK? = null
    private var mouseHook: WinUser.HHOOK? = null
    private var threadId = 0
    private var thread: Thread? = null

    // Geri cagri nesneleri GC'ye karsi alan olarak tutulur.
    private val kbProc = object : WinUser.LowLevelKeyboardProc {
        override fun callback(nCode: Int, wParam: WinDef.WPARAM, info: WinUser.KBDLLHOOKSTRUCT): WinDef.LRESULT {
            if (nCode >= 0) {
                val msg = wParam.toInt()
                val down = msg == 0x100 || msg == 0x104
                val up = msg == 0x101 || msg == 0x105
                if (down || up) {
                    val ours = info.dwExtraInfo?.toLong() == INJECT_MARK
                    val swallow = try {
                        onKey(info.vkCode, info.scanCode, info.flags, down, ours)
                    } catch (t: Throwable) {
                        false // hata asla klavyeyi kilitlememeli
                    }
                    if (swallow) return WinDef.LRESULT(1)
                }
            }
            return u32.CallNextHookEx(kbHook, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
        }
    }

    private val mouseProc = object : LowLevelMouseProc {
        override fun callback(nCode: Int, wParam: WinDef.WPARAM, lParam: Pointer): WinDef.LRESULT {
            if (nCode >= 0) {
                when (wParam.toInt()) {
                    0x201, 0x204, 0x207 -> runCatching { onMouseDown() } // sol/sag/orta tus
                }
            }
            return u32.CallNextHookEx(mouseHook, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(lParam)))
        }
    }

    fun start() {
        if (thread != null) return
        thread = Thread({
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val hMod = Kernel32.INSTANCE.GetModuleHandle(null)
            kbHook = u32.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, kbProc, hMod, 0)
            mouseHook = u32.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseProc, hMod, 0)
            val msg = WinUser.MSG()
            while (u32.GetMessage(msg, null, 0, 0) > 0) {
                u32.TranslateMessage(msg)
                u32.DispatchMessage(msg)
            }
            kbHook?.let { u32.UnhookWindowsHookEx(it) }
            mouseHook?.let { u32.UnhookWindowsHookEx(it) }
        }, "radexpand-hook").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    val isActive: Boolean get() = kbHook != null

    fun stop() {
        if (threadId != 0) u32.PostThreadMessage(threadId, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        thread = null
    }
}
