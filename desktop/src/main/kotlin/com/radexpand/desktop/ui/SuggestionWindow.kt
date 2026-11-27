package com.radexpand.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.radexpand.core.AppSettings
import com.radexpand.desktop.Host
import com.radexpand.ui.RadTheme
import java.awt.GraphicsEnvironment

/** Ekran pikseli -> pencere birimi (Windows olcekleme %125, %150...). */
private fun screenScale(): Double = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
}.getOrDefault(1.0)

/**
 * Kelime onerisi kutusu: yazi imlecinin hemen altinda kucuk bir pencere. Odak ALMAZ (yazdiginiz program
 * odakta kalir). Tab ilk oneriyi yazar; kelimeye tiklamak da yazar.
 */
@Composable
fun SuggestionWindow(current: Host.SuggestionState?, settings: AppSettings) {
    // Pencere bir kez olusturulur; oneri yokken yalnizca gizlenir (her harfte yeni pencere acilmasin).
    val lastShown = remember { arrayOf<Host.SuggestionState?>(null) }
    if (current != null) lastShown[0] = current
    val st = lastShown[0] ?: return
    val scale = remember { screenScale() }
    val state = rememberWindowState(
        position = WindowPosition((st.x / scale).dp, (st.y / scale + 4).dp),
        size = DpSize(Dp.Unspecified, Dp.Unspecified),
    )
    LaunchedEffect(st.x, st.y) { state.position = WindowPosition((st.x / scale).dp, (st.y / scale + 4).dp) }
    // Icerige gore genislik (kelimeler degisince pencere de buyur/kuculur)
    LaunchedEffect(st.words) { state.size = DpSize((st.words.sumOf { it.length } * 9 + st.words.size * 28 + 40).dp, 32.dp) }

    Window(
        onCloseRequest = {},
        visible = current != null,
        state = state,
        title = "RadExpand",
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
    ) {
        RadTheme(settings) {
            Surface(tonalElevation = 4.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    st.words.forEachIndexed { i, w ->
                        Text(
                            if (i == 0) "$w  ⇥" else w,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { Host.acceptSuggestionAt(i) }.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        if (i < st.words.size - 1) Text("·", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
