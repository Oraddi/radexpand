package com.radexpand.app

import com.radexpand.core.DiagSink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bellekte tutulan tani kaydi (diske/internete yazilmaz, uygulama kapaninca silinir).
 * Yazilan metin ASLA kaydedilmez: yalnizca tus turleri, uygulama adlari, kisayol adlari, sureler.
 */
object DiagLog : DiagSink {
    private const val MAX = 500
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var listener: (() -> Unit)? = null

    /** Ayarlardan kapatilabilir (A8). */
    @Volatile
    var enabled = true

    @Synchronized
    override fun log(event: String) {
        if (!enabled) return
        lines.addLast(fmt.format(Date()) + "  " + event)
        while (lines.size > MAX) lines.removeFirst()
        listener?.invoke()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        listener?.invoke()
    }

    @Synchronized
    fun setListener(l: (() -> Unit)?) {
        listener = l
    }
}
