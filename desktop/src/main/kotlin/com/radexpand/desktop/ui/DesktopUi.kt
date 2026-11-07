@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.radexpand.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radexpand.core.AppSettings
import com.radexpand.core.DataCodec
import com.radexpand.core.EspansoExport
import com.radexpand.core.EspansoImport
import com.radexpand.core.EspansoReport
import com.radexpand.core.RadExpandData
import com.radexpand.core.SyncMerge
import com.radexpand.desktop.Autostart
import com.radexpand.desktop.DesktopDiag
import com.radexpand.desktop.DesktopStats
import com.radexpand.desktop.DesktopStore
import com.radexpand.desktop.DesktopSync
import com.radexpand.desktop.EspansoWatch
import com.radexpand.desktop.Files2
import com.radexpand.desktop.Host
import com.radexpand.desktop.Updater
import com.radexpand.ui.ChoiceRow
import com.radexpand.ui.PerAppSection
import com.radexpand.core.KnownApps
import com.radexpand.ui.GuideScreen
import com.radexpand.ui.Hint
import com.radexpand.ui.ImportPreviewDialog
import com.radexpand.ui.Ok
import com.radexpand.ui.Onboarding
import com.radexpand.ui.PlatformSettings
import com.radexpand.ui.SectionTitle
import com.radexpand.ui.SettingsScreen
import com.radexpand.ui.ShortcutsScreen
import com.radexpand.ui.SwitchRow
import com.radexpand.ui.importSamplePack
import com.radexpand.ui.t
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab { SHORTCUTS, TRANSFER, SETTINGS, GUIDE, TEST }

@Composable
fun DesktopRoot(onHide: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.SHORTCUTS) }
    var pickRequest by remember { mutableStateOf(false) }
    val data by DesktopStore.data.collectAsState()
    val usage by DesktopStats.usage.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val espanso by EspansoWatch.running.collectAsState()
    val upd by Updater.state.collectAsState()

    fun sample() {
        val n = importSamplePack(DesktopStore)
        scope.launch { snackbar.showSnackbar(t("Örnek paketten $n kısayol eklendi.", "$n shortcuts added from the sample pack.")) }
    }

    if (!data.settings.onboardingDone) {
        Onboarding(
            DesktopStore,
            setupStep = {
                Text(t("RadExpand arka planda (sistem tepsisinde) çalışır ve her programda yazarken kısayolları genişletir.", "RadExpand runs in the background (system tray) and expands shortcuts while you type in any program."))
                espanso?.let { other -> Text(t("⚠ $other şu an çalışıyor. İkisi birlikte çalışırsa kısayollar iki kez genişler: $other programını kapatın.", "⚠ $other is running. If both run together, shortcuts expand twice: close $other."), color = MaterialTheme.colorScheme.error) }
                if (Autostart.exePath != null) {
                    var auto by remember { mutableStateOf(Autostart.isEnabled()) }
                    SwitchRow(t("Windows ile başlat", "Start with Windows"), t("Oturum açınca tepside başlar.", "Starts in the tray when you sign in."), auto) { v -> if (Autostart.set(v)) auto = v }
                }
            },
            onImportSample = { sample() },
            onImportEspanso = { tab = Tab.TRANSFER; pickRequest = true },
            onFinish = {},
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            NavigationRail(header = { Text("RadExpand", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp)) }) {
                NavigationRailItem(tab == Tab.SHORTCUTS, { tab = Tab.SHORTCUTS }, { Icon(Icons.Default.List, null) }, label = { Text(t("Kısayollar", "Shortcuts")) })
                NavigationRailItem(tab == Tab.TRANSFER, { tab = Tab.TRANSFER }, { Icon(Icons.Default.Share, null) }, label = { Text(t("Aktar / Senkron", "Import / Sync")) })
                NavigationRailItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text(t("Ayarlar", "Settings")) })
                NavigationRailItem(tab == Tab.GUIDE, { tab = Tab.GUIDE }, { Icon(Icons.Default.Info, null) }, label = { Text(t("Nasıl kullanılır", "How to use")) })
                NavigationRailItem(tab == Tab.TEST, { tab = Tab.TEST }, { Icon(Icons.Default.Build, null) }, label = { Text(t("Test", "Test")) })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onHide, modifier = Modifier.padding(8.dp)) { Text(t("Tepsiye küçült", "Minimize to tray")) }
            }
            Column(Modifier.weight(1f)) {
                espanso?.let { other -> Banner(t("$other da çalışıyor", "$other is also running"), t("İkisi aynı anda çalışırsa kısayollar iki kez genişler ve metin karışır. $other programını kapatın (tepsi simgesi → Çıkış/Exit) ve Windows ile başlamasını kapatın.", "If both run at the same time, shortcuts expand twice and the text gets mixed up. Close $other (tray icon → Exit) and stop it from starting with Windows.")) }
                if (!Host.hookActive) Banner(t("Klavye dinlenemiyor", "Cannot listen to the keyboard"), t("Windows klavye kancasını kuramadı. RadExpand'i yeniden başlatın.", "Windows could not install the keyboard hook. Restart RadExpand."))
                if (Host.paused) Banner(t("RadExpand duraklatıldı", "RadExpand is paused"), t("Tepsi simgesine sağ tıklayıp \"Devam et\" seçin.", "Right-click the tray icon and choose \"Resume\"."))
                if (upd.updateAvailable) {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Yeni sürüm var: ", "New version available: ") + upd.latest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { tab = Tab.SETTINGS }) { Text(t("Güncelleme…", "Update…")) }
                        }
                    }
                }
                when (tab) {
                    Tab.SHORTCUTS -> ShortcutsScreen(data, snackbar, DesktopStore, usage)
                    Tab.TRANSFER -> TransferScreen(data, snackbar, pickRequest) { pickRequest = false }
                    Tab.SETTINGS -> WindowsSettings(data, onExit)
                    Tab.GUIDE -> GuideScreen(isWindows = true, onTryIt = { tab = Tab.TEST }, onImportSample = { sample() })
                    Tab.TEST -> TestScreen()
                }
            }
        }
    }
}

@Composable
private fun Banner(title: String, text: String) {
    Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

@Composable
private fun TransferScreen(data: RadExpandData, snackbar: SnackbarHostState, pickRequest: Boolean, onPickRequested: () -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<List<EspansoReport>?>(null) }
    var restoreAsk by remember { mutableStateOf<File?>(null) }
    var tick by remember { mutableStateOf(0) }
    val sync by DesktopSync.state.collectAsState()
    fun say(m: String) = scope.launch { snackbar.showSnackbar(m) }

    fun pick() {
        val files = Files2.openMany(t("Espanso / RadExpand dosyası seçin", "Choose an Espanso / RadExpand file"), Files2.espansoMatchDir)
        val reports = ArrayList<EspansoReport>()
        for (f in files) {
            val text = try {
                f.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                say(t("${f.name} okunamadı", "Could not read ${f.name}"))
                continue
            }
            if (text.removePrefix("﻿").trimStart().startsWith("{")) {
                runCatching {
                    val other = DataCodec.decode(text)
                    val r = SyncMerge.merge(DesktopStore.data.value.shortcuts, other.shortcuts)
                    DesktopStore.backup("json-ice-aktarma-oncesi")
                    DesktopStore.update { it.copy(shortcuts = r.shortcuts) }
                    say(t("${f.name}: ${r.fromRemote} kısayol eklendi/güncellendi.", "${f.name}: ${r.fromRemote} shortcuts added/updated."))
                }.onFailure { say(t("${f.name} bir RadExpand dosyası değil.", "${f.name} is not a RadExpand file.")) }
            } else {
                reports += EspansoImport.parse(f.name, text, noWord = DesktopStore.data.value.settings.espansoNoWordMode)
            }
        }
        if (reports.isNotEmpty()) preview = reports
    }

    if (pickRequest) {
        onPickRequested()
        pick()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        SectionTitle(t("Espanso'dan ya da yedekten içe aktar", "Import from Espanso or a backup"))
        Hint(t("base.yml gibi Espanso dosyalarını ya da RadExpand yedeğini (.json) seçin (Espanso klasörü açılır). Önce önizleme gösterilir; orijinal dosya değiştirilmez.", "Choose Espanso files such as base.yml or a RadExpand backup (.json) (the Espanso folder opens). You see a preview first; the original file is not changed."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { pick() }) { Text(t("Dosya seç (.yml / .json)…", "Choose file (.yml / .json)…")) }
            OutlinedButton(onClick = { val n = importSamplePack(DesktopStore); say(t("Örnek paketten $n kısayol eklendi.", "$n shortcuts added from the sample pack.")) }) { Text(t("Örnek radyoloji paketi", "Sample radiology pack")) }
        }

        SectionTitle(t("Tablet ile senkron", "Sync with the tablet"))
        Hint(
            t(
                "Tablet ve bu bilgisayar aynı dosyayı kullanır (ör. Google Drive klasöründe radexpand-sync.json). Dosya 5 saniyede bir kontrol edilir; buradaki değişiklikler 2 saniye sonra yazılır. Aynı kısayol iki yerde değiştiyse son değişiklik kazanır; silmeler de taşınır.",
                "The tablet and this computer use the same file (e.g. radexpand-sync.json in a Google Drive folder). The file is checked every 5 seconds; changes here are written after 2 seconds. If a shortcut changed in both places, the latest change wins; deletions are synced too.",
            ),
        )
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sync.path == null) {
                    Text(t("Senkron dosyası bağlı değil.", "No sync file linked."))
                    Button(onClick = { Files2.save(t("Senkron dosyası oluştur / seç", "Create / choose sync file"), "radexpand-sync.json")?.let { DesktopSync.link(it) } }) { Text(t("Senkron dosyası seç / oluştur…", "Choose / create sync file…")) }
                } else {
                    Text(t("Bağlı dosya: ", "Linked file: ") + sync.path, fontWeight = FontWeight.Bold)
                    sync.lastSync?.let { Text(t("Son senkron: ", "Last sync: ") + it) }
                    sync.message?.let { Text(it, color = if (sync.error) MaterialTheme.colorScheme.error else Ok) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { DesktopSync.syncNow() }) { Text(t("Şimdi senkronla", "Sync now")) }
                        OutlinedButton(onClick = { DesktopSync.unlink() }) { Text(t("Bağlantıyı kaldır", "Unlink")) }
                    }
                }
            }
        }

        SectionTitle(t("Dışa aktar (yedek)", "Export (backup)"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                Files2.save(t("RadExpand yedeği", "RadExpand backup"), "radexpand-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json")?.let { out ->
                    runCatching { out.writeText(DataCodec.encode(DesktopStore.data.value)) }.onSuccess { say(t("Kaydedildi: ", "Saved: ") + out.name) }.onFailure { e -> say(t("Kaydedilemedi: ", "Could not save: ") + e.message) }
                }
            }) { Text(t("RadExpand (.json) — tam yedek", "RadExpand (.json) — full backup")) }
            OutlinedButton(onClick = {
                Files2.save(t("Espanso dosyası", "Espanso file"), "radexpand-espanso.yml")?.let { out ->
                    runCatching { out.writeText(EspansoExport.export(DesktopStore.data.value.shortcuts, includeDisabled = DesktopStore.data.value.settings.exportDisabled)) }
                        .onSuccess { say(t("Kaydedildi: ", "Saved: ") + out.name) }.onFailure { e -> say(t("Kaydedilemedi: ", "Could not save: ") + e.message) }
                }
            }) { Text("Espanso (.yml)") }
        }
        Hint(t("Tam yedek ayarları da içerir.", "The full backup includes settings."))

        SectionTitle(t("Otomatik yedekler", "Automatic backups"))
        Hint(t("Her gün ilk değişiklikte ve her içe aktarma, geri yükleme ve senkrondan önce (son ${data.settings.maxBackups}).", "At the first change of each day and before every import, restore and sync (last ${data.settings.maxBackups})."))
        val backups = remember(data, tick) { DesktopStore.backups() }
        backups.take(15).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.name.removePrefix("radexpand-").removeSuffix(".json"), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                TextButton(onClick = { restoreAsk = f }) { Text(t("Geri yükle", "Restore")) }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.padding(24.dp))
    }

    preview?.let { reps ->
        ImportPreviewDialog(reps, data, DesktopStore, onDismiss = { preview = null }, onDone = { m -> preview = null; tick++; say(m) })
    }
    restoreAsk?.let { f ->
        AlertDialog(
            onDismissRequest = { restoreAsk = null },
            title = { Text(t("Yedeği geri yükle?", "Restore this backup?")) },
            text = { Text(t("Kısayollarınız bu yedekteki hâline döner. Şimdiki hâliniz de ayrıca yedeklenir.", "Your shortcuts return to this backup. The current state is backed up too.")) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { DesktopStore.restoreBackup(f) }.onSuccess { say(t("Yedek geri yüklendi.", "Backup restored.")) }.onFailure { say(t("Geri yüklenemedi: ", "Could not restore: ") + it.message) }
                    restoreAsk = null
                    tick++
                }) { Text(t("Geri yükle", "Restore")) }
            },
            dismissButton = { TextButton(onClick = { restoreAsk = null }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
}

@Composable
private fun WindowsSettings(data: RadExpandData, onExit: () -> Unit) {
    val s = data.settings
    val recent by Host.recentApps.collectAsState()
    val upd by Updater.state.collectAsState()
    var autostart by remember { mutableStateOf(Autostart.isEnabled()) }
    var capturingPause by remember { mutableStateOf(false) }
    fun set(f: (AppSettings) -> AppSettings) = DesktopStore.update { it.copy(settings = f(it.settings)) }

    SettingsScreen(
        data, DesktopStore,
        PlatformSettings(
            isWindows = true,
            supportsDynamicColor = false,
            startKeyCapture = { cb -> Host.captureKey = { combo, label -> cb(combo.encode(), label) } },
            cancelKeyCapture = { Host.captureKey = null },
            bottom = {
                SectionTitle("Windows")
                SwitchRow(
                    t("Tıklamadan sonraki ilk kelime kelime başı sayılsın", "Treat the first word after a click as a word start"),
                    t(
                        "Windows imlecin solundaki metni programlara söylemez. Açıkken (Espanso gibi) fareyle tıkladıktan ya da pencere değiştirdikten sonra yazılan ilk kısayol genişler. Kapalıyken önce bir boşluk/Enter görmeden genişletmez.",
                        "Windows does not tell programs the text before the cursor. When on (like Espanso), the first shortcut typed after a click or window switch expands. When off, it waits for a space/Enter first.",
                    ),
                    s.unknownStartIsBoundary,
                ) { v -> set { it.copy(unknownStartIsBoundary = v) } }
                if (Autostart.exePath == null) {
                    Hint(t("\"Windows ile başlat\" yalnızca kurulu RadExpand ile kullanılabilir.", "\"Start with Windows\" is available only in the installed RadExpand."))
                } else {
                    SwitchRow(t("Windows ile başlat", "Start with Windows"), t("Oturum açınca tepside küçültülmüş başlar.", "Starts minimized in the tray when you sign in."), autostart) { v -> if (Autostart.set(v)) autostart = v }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Duraklat / devam kısayolu", "Pause / resume shortcut"), fontWeight = FontWeight.Medium)
                        Hint(if (s.pauseHotkey == null) t("Atanmadı (ör. Ctrl+Alt+P)", "Not assigned (e.g. Ctrl+Alt+P)") else s.pauseHotkeyLabel)
                    }
                    OutlinedButton(onClick = {
                        capturingPause = true
                        Host.captureKey = { combo, label ->
                            capturingPause = false
                            if (combo.mods != 0) set { it.copy(pauseHotkey = combo.encode(), pauseHotkeyLabel = label) }
                        }
                    }) { Text(t("Tuş ata", "Assign key")) }
                    if (s.pauseHotkey != null) TextButton(onClick = { set { it.copy(pauseHotkey = null, pauseHotkeyLabel = "") } }) { Text(t("Kaldır", "Remove")) }
                }
                ChoiceRow(t("Yazma hızı", "Typing speed")) {
                    listOf(0 to t("En hızlı", "Fastest"), 5 to t("Normal", "Normal"), 15 to t("Yavaş", "Slow"), 40 to t("Çok yavaş", "Very slow")).forEach { (v, l) ->
                        FilterChip(s.typingDelayMs == v, { set { it.copy(typingDelayMs = v) } }, { Text(l) })
                    }
                }
                Hint(t("Bazı programlar hızlı gelen tuşları kaçırabilir; o zaman yavaşlatın.", "Some programs miss fast keystrokes; slow down if that happens."))
                ChoiceRow(t("Uzun metinleri pano ile yapıştır", "Paste long texts via clipboard")) {
                    listOf(0 to t("Hiçbir zaman", "Never"), 200 to t("200+ karakter", "200+ characters"), 500 to t("500+ karakter", "500+ characters")).forEach { (v, l) ->
                        FilterChip(s.pasteLongerThan == v, { set { it.copy(pasteLongerThan = v) } }, { Text(l) })
                    }
                }
                Hint(t("Panodaki önceki metin yapıştırmadan sonra geri konur.", "The previous clipboard text is restored after pasting."))

                // Windows'ta .exe adlari kucuk harfle tutulur (WINWORD.EXE = winword.exe).
                val disabledLower = s.disabledPackages.map { it.lowercase() }.toSet()
                PerAppSection(
                    known = KnownApps.windows,
                    seen = recent.map { it.lowercase() },
                    disabled = disabledLower,
                    labelOf = { id -> KnownApps.windows.firstOrNull { it.id == id }?.name ?: id },
                ) { id, off ->
                    set { st ->
                        val cur = st.disabledPackages.map { it.lowercase() }.toSet()
                        st.copy(disabledPackages = if (off) cur + id else cur - id)
                    }
                }

                SectionTitle(t("Güncelleme", "Updates"))
                Text(t("Bu sürüm: ", "This version: ") + Updater.currentVersion)
                SwitchRow(
                    t("Güncellemeleri otomatik kontrol et", "Check for updates automatically"),
                    t("Açılışta ve günde bir kez GitHub'a tek küçük bir istek gönderilir; yazdığınız hiçbir şey gönderilmez. Sormadan kurmaz.", "One small request to GitHub at startup and once a day; nothing you type is sent. Never installs without asking."),
                    s.checkUpdates,
                ) { v -> set { it.copy(checkUpdates = v) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { Updater.checkNow() }, enabled = !upd.checking && !upd.downloading) { Text(if (upd.checking) t("Kontrol ediliyor…", "Checking…") else t("Şimdi kontrol et", "Check now")) }
                    if (upd.updateAvailable && upd.installerUrl != null) {
                        Button(onClick = { Updater.downloadAndInstall(onExit) }, enabled = !upd.downloading) { Text(t("İndir ve kur", "Download and install") + " (${upd.latest})") }
                    }
                    if (upd.updateAvailable) {
                        TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(upd.pageUrl)) } }) { Text(t("Sürüm notları", "Release notes")) }
                    }
                }
                if (upd.downloading) LinearProgressIndicator(progress = { upd.progress }, modifier = Modifier.fillMaxWidth())
                upd.message?.let { Hint(it) }

                SectionTitle(t("Bilinen Windows sınırlamaları", "Known Windows limitations"))
                Hint(t("• Yönetici olarak çalışan programlarda (bazı RIS/PACS istemcileri) RadExpand'in de yönetici olarak çalıştırılması gerekir.", "• In programs running as administrator (some RIS/PACS clients), RadExpand must also run as administrator."))
                Hint(t("• Parola alanları Windows'ta genel olarak algılanamaz; parola yöneticinizi yukarıdan kapatın.", "• Password fields cannot generally be detected on Windows; turn off your password manager above."))
                SectionTitle(t("Hakkında", "About"))
                Text("RadExpand ${Updater.currentVersion} · MIT · github.com/${Updater.REPO}")
                Hint(t("RadExpand bir tıbbi cihaz değildir; oluşan metni kontrol etmek kullanıcının sorumluluğundadır.", "RadExpand is not a medical device; checking the resulting text is the user's responsibility."))
            },
        ),
    )
    if (capturingPause) {
        AlertDialog(
            onDismissRequest = { Host.captureKey = null; capturingPause = false },
            title = { Text(t("Tuşa basın", "Press a key")) },
            text = { Text(t("Ctrl, Alt ya da Win ile birlikte bir tuşa basın (ör. Ctrl+Alt+P).", "Press a key together with Ctrl, Alt or Win (e.g. Ctrl+Alt+P).")) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { Host.captureKey = null; capturingPause = false }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
}

@Composable
private fun TestScreen() {
    val lines by DesktopDiag.lines.collectAsState()
    val stats by DesktopStats.stats.collectAsState()
    var text by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        Host.testScreenActive = true
        onDispose { Host.testScreenActive = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(t("Deneme alanı", "Practice area"))
        Hint(t("Bu sayfa açıkken RadExpand burada da çalışır (diğer RadExpand sayfalarında çalışmaz; kısayol düzenlerken metin genişlemesin diye).", "While this page is open RadExpand works here too (not on other RadExpand pages, so text doesn't expand while you edit shortcuts)."))
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), placeholder = { Text(t("ör. bir ri odak izş", "e.g. a hyi focus")) })
        SectionTitle(t("Programlara göre", "By program"))
        if (stats.isEmpty()) Hint(t("Henüz genişletme yok.", "No expansions yet."))
        stats.forEach { (exe, st) -> Text("$exe: ${st.expansions} " + t("genişletme", "expansions") + ", ${st.undos} " + t("geri alma", "undos")) }
        SectionTitle(t("Tanılama kaydı (yazdığınız metin kaydedilmez)", "Diagnostic log (what you type is not recorded)"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { DesktopDiag.clear() }) { Text(t("Temizle", "Clear")) } }
        SelectionContainer { Text(lines.asReversed().take(200).joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.padding(24.dp))
    }
}
