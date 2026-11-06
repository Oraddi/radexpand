package com.radexpand.ui

// Bu klasordeki dosyalar hem Android uygulamasinda hem Windows uygulamasinda derlenir.
// Yalnizca Compose'un ortak API'leri ve core modulu kullanilmalidir (Android'e ozgu kod yok).

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radexpand.core.Accent
import com.radexpand.core.AppSettings
import com.radexpand.core.Lang
import com.radexpand.core.RadExpandData
import com.radexpand.core.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** Kisayol verisinin saklandigi yer (Android: Repo, Windows: DesktopStore). */
interface DataStore {
    val data: StateFlow<RadExpandData>
    fun update(transform: (RadExpandData) -> RadExpandData): RadExpandData
    fun backup(reason: String)
}

/** Arayuz dili; degisince ekranlar yeniden cizilir. */
object UiLang {
    private var state by mutableStateOf(Lang.lang)
    var lang: String
        get() = state
        set(v) {
            state = v
            Lang.lang = v
        }
}

/** Iki dilli metin: t("Kaydet", "Save"). */
fun t(tr: String, en: String): String = if (UiLang.lang == "en") en else tr

val Ok = Color(0xFF2E7D32)
val Warn = Color(0xFFE65100)

fun now() = System.currentTimeMillis()

fun accentColor(a: Accent): Color = when (a) {
    Accent.TEAL, Accent.DYNAMIC -> Color(0xFF00796B)
    Accent.BLUE -> Color(0xFF1565C0)
    Accent.PURPLE -> Color(0xFF6A1B9A)
    Accent.ORANGE -> Color(0xFFE65100)
    Accent.GREEN -> Color(0xFF2E7D32)
}

/**
 * Tema: sistem/acik/koyu, vurgu rengi, yazi boyutu (A5).
 * [dynamic]: Android 12+ duvar kagidi renkleri (platform verir; yoksa null).
 */
@Composable
fun RadTheme(settings: AppSettings, dynamic: @Composable (dark: Boolean) -> ColorScheme? = { null }, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = accentColor(settings.accent)
    val scheme = (if (settings.accent == Accent.DYNAMIC) dynamic(dark) else null)
        ?: if (dark) {
            darkColorScheme(primary = accent.copy(alpha = 1f).lighten(), secondary = accent.lighten(), tertiary = accent.lighten())
        } else {
            lightColorScheme(primary = accent, secondary = accent, tertiary = accent)
        }
    val d = LocalDensity.current
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * settings.textScale.coerceIn(0.8f, 1.4f))) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

private fun Color.lighten(): Color = Color(red + (1 - red) * 0.45f, green + (1 - green) * 0.45f, blue + (1 - blue) * 0.45f, alpha)

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

@Composable
fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Kisa etiket (mod, pasif vb.). */
@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 6.dp)) {
        Text(text, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (hint.isNotEmpty()) Hint(hint)
        }
        Switch(checked, onChange)
    }
}
