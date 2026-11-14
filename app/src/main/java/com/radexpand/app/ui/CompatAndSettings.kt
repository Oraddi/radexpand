@file:OptIn(ExperimentalLayoutApi::class)

package com.radexpand.app.ui

import androidx.compose.material3.Button
import com.radexpand.ui.PerAppSection
import com.radexpand.core.KnownApps
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radexpand.app.CompatStats
import com.radexpand.app.KeyMapper
import com.radexpand.app.Repo
import com.radexpand.app.TestActivity
import com.radexpand.core.AppSettings
import com.radexpand.core.DisconnectAction
import com.radexpand.core.RadExpandData
import com.radexpand.ui.ChoiceRow
import com.radexpand.ui.Hint
import com.radexpand.ui.Ok
import com.radexpand.ui.PlatformSettings
import com.radexpand.ui.SectionTitle
import com.radexpand.ui.SettingsScreen
import com.radexpand.ui.SwitchRow
import com.radexpand.ui.Warn
import com.radexpand.ui.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun setSettings(f: (AppSettings) -> AppSettings) = Repo.update { it.copy(settings = f(it.settings)) }

/**
 * Ayarlarda "Tus ata": ana ekran bir sonraki fiziksel tusu buraya verir (bkz. MainActivity.dispatchKeyEvent).
 * Kendi ekranlarimizda klavye servisi genisletme yapmadigi icin tus uygulamaya ulasir.
 */
object KeyCapture {
    @Volatile
    var pending: ((String?, String) -> Unit)? = null

    /** true donerse olay tuketildi. */
    fun handle(e: KeyEvent): Boolean {
        val cb = pending ?: return false
        if (e.action != KeyEvent.ACTION_DOWN || KeyEvent.isModifierKey(e.keyCode)) return true
        pending = null
        if (KeyMapper.assignable(e)) cb("${e.keyCode}:${KeyMapper.mods(e)}", KeyMapper.label(e)) else cb(null, "")
        return true
    }
}

@Composable
fun AndroidSettings(data: RadExpandData) {
    val ctx = LocalContext.current
    SettingsScreen(
        data, Repo,
        PlatformSettings(
            isWindows = false,
            supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            startKeyCapture = { cb -> KeyCapture.pending = cb },
            cancelKeyCapture = { KeyCapture.pending = null },
            top = { KeyboardSetupCard(alwaysShow = true) },
            bottom = {
                val stats by CompatStats.stats.collectAsState()
                // Yalnizca yuklu yaygin uygulamalar (manifest'teki <queries> sayesinde gorunur); internet gerekmez.
                val installed = remember {
                    KnownApps.android.filter { app ->
                        runCatching { ctx.packageManager.getApplicationInfo(app.id, 0) }.isSuccess
                    }
                }
                PerAppSection(
                    known = installed,
                    seen = stats.entries.sortedByDescending { it.value.lastUsed }.map { it.key },
                    disabled = data.settings.disabledPackages,
                    labelOf = { CompatStats.label(it) },
                ) { id, off ->
                    setSettings { it.copy(disabledPackages = if (off) it.disabledPackages + id else it.disabledPackages - id) }
                }
                SectionTitle(t("Fiziksel klavye çıkınca", "When the physical keyboard is disconnected"))
                ChoiceRow("") {
                    if (Build.VERSION.SDK_INT >= 28) {
                        FilterChip(data.settings.onKeyboardDisconnect == DisconnectAction.SWITCH_PREVIOUS, { setSettings { it.copy(onKeyboardDisconnect = DisconnectAction.SWITCH_PREVIOUS) } }, { Text(t("Ekran klavyesine otomatik geç", "Switch to on-screen keyboard")) })
                    }
                    FilterChip(data.settings.onKeyboardDisconnect == DisconnectAction.SHOW_BAR, { setSettings { it.copy(onKeyboardDisconnect = DisconnectAction.SHOW_BAR) } }, { Text(t("Alt şeridi göster", "Show the bottom bar")) })
                }
                Hint(
                    t(
                        "RadExpand'in ekran klavyesi yoktur. Fiziksel klavye ayrılınca otomatik olarak ekran klavyesine (önce Samsung Klavye, yoksa Gboard) geçilir. Klavyeyi geri takınca RadExpand'e dönüşü Android kendiliğinden yaptırmaz: alt çubuktaki klavye simgesine dokunup RadExpand'i seçin.",
                        "RadExpand has no on-screen keyboard. When the physical keyboard is disconnected, Android switches to the on-screen keyboard (Samsung Keyboard first, otherwise Gboard). Android does not switch back automatically when you reconnect it: tap the keyboard icon in the navigation bar and choose RadExpand.",
                    ),
                )
                SectionTitle(t("Güncelleme", "Updates"))
                val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
                Text(t("Bu sürüm: ", "This version: ") + version)
                Hint(
                    t(
                        "RadExpand'in internet izni yoktur; güncellemeyi tarayıcı indirir. \"Son sürümü indir\" GitHub'daki en yeni APK'yı tarayıcıda indirir. İndirme bitince bildirime dokunun → \"Güncelle\". Kısayollarınız ve ayarlarınız korunur. (İlk seferde Android, tarayıcıya \"bilinmeyen uygulama yükleme\" izni sorabilir.)",
                        "RadExpand has no internet permission; the browser downloads the update. \"Download latest version\" downloads the newest APK from GitHub in the browser. When it finishes, tap the notification → \"Update\". Your shortcuts and settings are kept. (The first time, Android may ask to allow the browser to install unknown apps.)",
                    ),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUrl(ctx, "https://github.com/Oraddi/radexpand/releases/latest/download/RadExpand.apk") }) { Text(t("Son sürümü indir", "Download latest version")) }
                    OutlinedButton(onClick = { openUrl(ctx, "https://github.com/Oraddi/radexpand/releases/latest") }) { Text(t("Sürüm notları", "Release notes")) }
                }
                Hint(t("Otomatik takip için: Obtainium uygulamasına github.com/Oraddi/radexpand adresini ekleyin; yeni sürüm çıkınca haber verir.", "For automatic tracking: add github.com/Oraddi/radexpand to the Obtainium app; it notifies you of new versions."))
                SectionTitle(t("Hakkında", "About"))
                Text("RadExpand $version · MIT")
                Hint(
                    t(
                        "Kısayollar yalnızca bu cihazda (ve sizin seçtiğiniz senkron dosyasında) durur. Yazdığınız hiçbir şey cihazdan çıkmaz.",
                        "Shortcuts live only on this device (and in the sync file you choose). Nothing you type leaves the device.",
                    ),
                )
            },
        ),
    )
}

private fun statusOf(s: CompatStats.AppStat): Pair<String, Boolean> = when {
    s.lookups == 0 -> t("Henüz veri yok", "No data yet") to true
    s.editorText == 0 -> t("Editör metin vermiyor → kör mod (kendi tamponuyla genişletir)", "The editor does not report text → blind mode") to false
    s.slow > 0 -> t("Bazen yavaş yanıt (${s.slow} kez ≥400 ms) → o alanda kör moda geçildi", "Sometimes slow (${s.slow}× ≥400 ms) → blind mode in that field") to false
    s.noText > 0 -> t("Çoğunlukla uyumlu (${s.noText} kez metin alınamadı)", "Mostly compatible (${s.noText}× no text)") to false
    else -> t("✓ Tam uyumlu: editör metni veriyor", "✓ Fully compatible: the editor reports text") to true
}

@Composable
fun CompatScreen(data: RadExpandData) {
    val ctx = LocalContext.current
    val stats by CompatStats.stats.collectAsState()
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val apps = stats.entries.sortedByDescending { it.value.lastUsed }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        SectionTitle(t("Uygulama uyumluluğu", "App compatibility"))
        Hint(
            t(
                "RadExpand'in kullandığınız her uygulamada nasıl çalıştığı burada otomatik toplanır: kaç kez genişlettiği, editörün metin verip vermediği ve gecikme. Yazdığınız metin kaydedilmez. \"Geri alma\" sayısı yüksekse o uygulamada istenmeyen genişleme oluyor olabilir.",
                "How RadExpand works in each app you use is collected here automatically: expansions, whether the editor reports text, and delay. What you type is not recorded. A high \"undo\" count may mean unwanted expansions in that app.",
            ),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { copyReport(ctx, stats, data) }) { Text(t("Raporu kopyala", "Copy report")) }
            OutlinedButton(onClick = { CompatStats.reset() }) { Text(t("Sıfırla", "Reset")) }
            OutlinedButton(onClick = { ctx.startActivity(Intent(ctx, TestActivity::class.java)) }) { Text(t("Test ekranı", "Test screen")) }
        }
        if (apps.isEmpty()) Text(t("Henüz veri yok. RadExpand klavye olarak seçiliyken başka bir uygulamada yazın.", "No data yet. Type in another app while RadExpand is selected as the keyboard."), modifier = Modifier.padding(top = 8.dp))
        apps.forEach { (pkg, s) ->
            val (status, good) = statusOf(s)
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(CompatStats.label(pkg), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (CompatStats.label(pkg) != pkg) Hint(pkg)
                    Text(status, color = if (good) Ok else Warn)
                    Text(t("Genişletme", "Expansions") + ": ${s.expansions} · " + t("Geri alma", "Undos") + ": ${s.undos} · " + t("Editör metni", "Editor text") + ": ${s.editorText}/${s.lookups} · " + t("Ortalama", "Average") + " ${s.avgMs} ms (max ${s.maxMs} ms) · ${fmt.format(Date(s.lastUsed))}")
                    SwitchRow(t("Bu uygulamada kapat", "Turn off in this app"), t("RadExpand bu uygulamada hiç genişletmez.", "RadExpand never expands in this app."), pkg in data.settings.disabledPackages) { on ->
                        setSettings { it.copy(disabledPackages = if (on) it.disabledPackages + pkg else it.disabledPackages - pkg) }
                    }
                    SwitchRow(
                        t("Kör modu zorla", "Force blind mode"),
                        t("Editöre metin sorulmaz; RadExpand yalnızca kendi gördüğü tuşlarla çalışır (yavaş ya da hatalı metin veren uygulamalar için).", "The editor is not asked for text; RadExpand uses only the keys it saw (for slow or misbehaving apps)."),
                        pkg in data.settings.blindPackages,
                    ) { on ->
                        setSettings { it.copy(blindPackages = if (on) it.blindPackages + pkg else it.blindPackages - pkg) }
                    }
                }
            }
        }
        Spacer(Modifier.padding(24.dp))
    }
}

private fun copyReport(ctx: Context, stats: Map<String, CompatStats.AppStat>, data: RadExpandData) {
    val text = buildString {
        appendLine("RadExpand compatibility · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Shortcuts: ${data.active.size}; keyboards: ${keyboardStatus(ctx).keyboards}")
        stats.entries.sortedByDescending { it.value.lastUsed }.forEach { (pkg, s) ->
            appendLine("${CompatStats.label(pkg)} [$pkg]: ${statusOf(s).first}; exp=${s.expansions}, undo=${s.undos}, text=${s.editorText}/${s.lookups}, avg=${s.avgMs}ms, max=${s.maxMs}ms" +
                (if (pkg in data.settings.disabledPackages) ", OFF" else "") + (if (pkg in data.settings.blindPackages) ", BLIND" else ""))
        }
    }
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RadExpand", text))
    android.widget.Toast.makeText(ctx, t("Rapor panoya kopyalandı", "Report copied to clipboard"), android.widget.Toast.LENGTH_SHORT).show()
}

@Suppress("unused")
private fun openKeyboardSettings(ctx: Context) = openSystem(ctx, Settings.ACTION_INPUT_METHOD_SETTINGS)

/** Baglantiyi tarayicida ac (uygulamanin kendisi internete cikmaz). */
private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(ctx, t("Tarayıcı açılamadı: ", "Could not open the browser: ") + url, android.widget.Toast.LENGTH_LONG).show() }
}
