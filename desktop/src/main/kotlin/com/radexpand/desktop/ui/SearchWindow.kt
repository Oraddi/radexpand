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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.radexpand.core.AppSettings
import com.radexpand.core.ShortcutSearch
import com.radexpand.desktop.Host
import com.radexpand.ui.RadTheme
import com.radexpand.ui.t

/**
 * Kisayol arama penceresi (Alt+Space). YALNIZCA GOSTERIR: odak almaz, tuslari [Host] klavye kancasindan
 * isler (yazdiginiz program odakta kalir, secilen metin dogrudan oraya yazilir).
 */
@Composable
fun SearchWindow(st: Host.SearchState, settings: AppSettings) {
    val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(680.dp, 440.dp))
    val listState = rememberLazyListState()

    Window(
        onCloseRequest = { Host.closeSearch() },
        state = state,
        title = "RadExpand",
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(st.selected, st.results.size) {
            if (st.results.isNotEmpty()) listState.scrollToItem((st.selected - 3).coerceAtLeast(0))
        }
        RadTheme(settings) {
            Surface(Modifier.fillMaxSize(), tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("Kısayol ara: ", "Search shortcuts: ") + st.query + "▏", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (st.results.isEmpty()) t("Sonuç yok · Esc: kapat", "No results · Esc: close")
                        else t("Yazarak arayın · ↑↓ seç · Enter yaz · Esc kapat · ${st.results.size} sonuç", "Type to search · ↑↓ select · Enter insert · Esc close · ${st.results.size} results"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    LazyColumn(state = listState) {
                        itemsIndexed(st.results) { i, s ->
                            val sel = i == st.selected
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable { Host.pickSearchAt(i) }
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
