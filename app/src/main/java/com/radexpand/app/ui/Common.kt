package com.radexpand.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radexpand.core.AppSettings
import com.radexpand.ui.Ok
import com.radexpand.ui.RadTheme
import com.radexpand.ui.t
import kotlinx.coroutines.flow.MutableStateFlow

/** Ekran on plana gelince artan sayac: klavye durumu gibi sistem bilgilerini tazelemek icin. */
object UiTicker {
    val tick = MutableStateFlow(0)
    fun bump() {
        tick.value++
    }
}

/** Ortak tema + Android 12+ duvar kagidi renkleri. */
@Composable
fun AndroidTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    RadTheme(
        settings,
        dynamic = { dark ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null
        },
        content = content,
    )
}

data class KeyboardStatus(val enabled: Boolean, val selected: Boolean, val hardware: Boolean, val keyboards: List<String>)

fun keyboardStatus(ctx: Context): KeyboardStatus {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
    val current = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull() ?: ""
    val cfg = ctx.resources.configuration
    val hw = cfg.keyboard == Configuration.KEYBOARD_QWERTY && cfg.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val kb = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        .filter { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }.map { it.name }
    return KeyboardStatus(enabled, current.startsWith("${ctx.packageName}/"), hw, kb)
}

fun openSystem(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun showPicker(ctx: Context) =
    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

/** Klavye kurulum durumu ve dugmeleri (ilk acilis sihirbazi ve Ayarlar'in ustu). */
@Composable
fun KeyboardSetupCard(alwaysShow: Boolean) {
    val ctx = LocalContext.current
    val tick by UiTicker.tick.collectAsState()
    val s = remember(tick) { keyboardStatus(ctx) }
    if (!alwaysShow && s.enabled && s.selected) return
    val ok = s.enabled && s.selected
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (ok) t("✓ RadExpand klavye olarak çalışıyor", "✓ RadExpand is working as your keyboard") else t("RadExpand henüz çalışmıyor", "RadExpand is not working yet"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text((if (s.enabled) "✓ " else "✗ ") + t("1. Klavye ayarlarında RadExpand açık", "1. RadExpand enabled in keyboard settings"))
            Text((if (s.selected) "✓ " else "✗ ") + t("2. Klavye olarak RadExpand seçili", "2. RadExpand selected as the keyboard"))
            Text(
                (if (s.hardware) "✓ " else "• ") + t("Fiziksel klavye: ", "Physical keyboard: ") + s.keyboards.joinToString().ifEmpty { t("algılanmadı", "not detected") },
                color = if (s.hardware) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ok) {
                Text(
                    t(
                        "Android, her klavye için \"yazdıklarınızı toplayabilir\" uyarısı gösterir. RadExpand'in internet izni yoktur; yazdıklarınız cihazdan çıkamaz.",
                        "Android shows a \"may collect what you type\" warning for every keyboard. RadExpand has no internet permission; what you type cannot leave the device.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.enabled) OutlinedButton(onClick = { openSystem(ctx, Settings.ACTION_INPUT_METHOD_SETTINGS) }) { Text(t("Klavye ayarlarını aç", "Open keyboard settings")) }
                if (!s.selected) OutlinedButton(onClick = { showPicker(ctx) }) { Text(t("Klavye seç", "Choose keyboard")) }
                OutlinedButton(onClick = { openSystem(ctx, Settings.ACTION_HARD_KEYBOARD_SETTINGS) }) { Text(t("Fiziksel klavye düzeni", "Physical keyboard layout")) }
            }
        }
    }
}
