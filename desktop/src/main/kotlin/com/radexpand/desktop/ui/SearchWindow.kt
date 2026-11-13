package com.radexpand.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.radexpand.core.ShortcutSearch
import com.radexpand.desktop.DesktopStats
import com.radexpand.desktop.Host
import com.radexpand.ui.t
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Kisayol arama penceresi (Alt+Space, Espanso'daki gibi). Yazarak arayin, ↑↓ secin, Enter ile onceki
 * pencereye yazdirin, Esc ile kapatin. Pencere odagi kaybedince kendiliginden kapanir.
 */
@Composable
fun SearchWindow(req: Host.SearchRequest, settings: com.radexpand.core.AppSettings) {
    val usage by DesktopStats.usage.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    val results = remember(query, usage) { ShortcutSearch.find(req.shortcuts, query, usage) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(680.dp, 460.dp))

    fun pick() {
        results.getOrNull(selected)?.let { Host.pickSearch(it) } ?: Host.closeSearch(true)
    }

    Window(
        onCloseRequest = { Host.closeSearch(true) },
        state = state,
        title = "RadExpand",
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            when (e.key) {
                Key.Escape -> { Host.closeSearch(true); true }
                Key.Enter, Key.NumPadEnter, Key.Tab -> { pick(); true }
                Key.DirectionDown -> { selected = (selected + 1).coerceAtMost((results.size - 1).coerceAtLeast(0)); true }
                Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                Key.PageDown -> { selected = (selected + 8).coerceAtMost((results.size - 1).coerceAtLeast(0)); true }
                Key.PageUp -> { selected = (selected - 8).coerceAtLeast(0); true }
                else -> false
            }
        },
    ) {
        // One gel ve odagi al (Windows bazen arka planda acar).
        LaunchedEffect(Unit) {
            window.toFront()
            runCatching { User32.INSTANCE.SetForegroundWindow(WinDef.HWND(Native.getWindowPointer(window))) }
            window.requestFocus()
            focus.requestFocus()
        }
        // Baska yere tiklaninca kapan.
        DisposableEffect(Unit) {
            val l = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) = Host.closeSearch(false)
            }
            window.addWindowFocusListener(l)
            onDispose { window.removeWindowFocusListener(l) }
        }
        LaunchedEffect(selected) { if (results.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(0, results.size - 1)) }

        com.radexpand.ui.RadTheme(settings) {
        Surface(Modifier.fillMaxSize(), tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    query, { query = it; selected = 0 },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    label = { Text(t("Kısayol ara", "Search shortcuts")) },
                )
                Text(
                    if (results.isEmpty()) t("Sonuç yok · Esc: kapat", "No results · Esc: close")
                    else t("↑↓ seç · Enter yaz · Esc kapat · ${results.size} sonuç", "↑↓ select · Enter insert · Esc close · ${results.size} results"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                LazyColumn(state = listState) {
                    itemsIndexed(results) { i, s ->
                        val sel = i == selected
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { selected = i; Host.pickSearch(s) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.trigger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ShortcutSearch.preview(s, 120), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        }
    }
}
