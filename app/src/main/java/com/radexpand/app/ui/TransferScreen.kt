@file:OptIn(ExperimentalMaterial3Api::class)

package com.radexpand.app.ui

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radexpand.app.Repo
import com.radexpand.app.SyncManager
import com.radexpand.ui.Hint
import com.radexpand.ui.ImportPreviewDialog
import com.radexpand.ui.Ok
import com.radexpand.ui.SectionTitle
import com.radexpand.ui.t
import com.radexpand.core.ConflictPolicy
import com.radexpand.core.DataCodec
import com.radexpand.core.EspansoExport
import com.radexpand.core.EspansoImport
import com.radexpand.core.EspansoReport
import com.radexpand.core.ImportPlanner
import com.radexpand.core.RadExpandData
import com.radexpand.core.SyncMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun readText(ctx: Context, uri: Uri): String =
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error(t("Dosya açılamadı", "Could not open file"))

private fun writeText(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error(t("Dosya yazılamadı", "Could not write file"))
}

private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

@Composable
fun TransferScreen(data: RadExpandData, snackbar: SnackbarHostState, pending: List<Uri>, onPendingConsumed: () -> Unit, requestPick: Boolean = false, onPickRequested: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<List<EspansoReport>?>(null) }
    var restoreAsk by remember { mutableStateOf<File?>(null) }
    val sync by SyncManager.state.collectAsState()
    var backupsTick by remember { mutableStateOf(0) }

    fun say(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    /** Secilen dosyalari oku: RadExpand JSON ise birlestir, degilse Espanso YAML olarak onizle. */
    fun load(uris: List<Uri>) = scope.launch {
        val reports = ArrayList<EspansoReport>()
        for (u in uris) {
            val name = SyncManager.displayName(u)
            val text = try {
                withContext(Dispatchers.IO) { readText(ctx, u) }
            } catch (e: Exception) {
                say(t("$name okunamadı: ${e.message}", "Could not read $name: ${e.message}"))
                continue
            }
            if (text.trimStart().startsWith("{")) {
                try {
                    val other = DataCodec.decode(text)
                    val r = SyncMerge.merge(Repo.data.value.shortcuts, other.shortcuts)
                    Repo.backup("json-ice-aktarma-oncesi")
                    Repo.update { it.copy(shortcuts = r.shortcuts) }
                    say(t("$name: ${r.fromRemote} kısayol eklendi/güncellendi.", "$name: ${r.fromRemote} shortcuts added/updated."))
                } catch (e: Exception) {
                    say(t("$name bir RadExpand dosyası değil: ${e.message}", "$name is not a RadExpand file: ${e.message}"))
                }
            } else {
                reports += try {
                    withContext(Dispatchers.Default) { EspansoImport.parse(name, text, noWord = Repo.data.value.settings.espansoNoWordMode) }
                } catch (e: Throwable) {
                    EspansoReport(name, emptyList(), emptyList(), emptyList(), emptyList(), error = t("Okunamadı: ", "Could not read: ") + e.message)
                }
            }
        }
        if (reports.isNotEmpty()) preview = reports
    }

    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            load(pending)
            onPendingConsumed()
        }
    }

    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) load(uris) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { writeText(ctx, uri, DataCodec.encode(Repo.data.value)) } }
                .onSuccess { say(t("RadExpand yedeği kaydedildi.", "RadExpand backup saved.")) }.onFailure { say(t("Kaydedilemedi: ", "Could not save: ") + it.message) }
        }
    }
    val exportYaml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-yaml")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { writeText(ctx, uri, EspansoExport.export(Repo.data.value.shortcuts, includeDisabled = Repo.data.value.settings.exportDisabled)) } }
                .onSuccess { say(t("Espanso dosyası kaydedildi.", "Espanso file saved.")) }.onFailure { say(t("Kaydedilemedi: ", "Could not save: ") + it.message) }
        }
    }
    val createSync = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { SyncManager.linkSync(it) } }
    val openSync = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { SyncManager.linkSync(it) } }
    val createYamlLink = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-yaml")) { uri -> uri?.let { SyncManager.linkYaml(it) } }

    LaunchedEffect(requestPick) {
        if (requestPick) {
            onPickRequested()
            pickImport.launch(arrayOf("*/*"))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        SectionTitle(t("Espanso'dan ya da yedekten içe aktar", "Import from Espanso or a backup"))
        Hint(t("base.yml gibi Espanso dosyalarını ya da RadExpand yedeğini (.json) seçin. Espanso dosyalarında önce bir önizleme gösterilir; hiçbir şey onayınız olmadan eklenmez. Orijinal dosya değiştirilmez.", "Choose Espanso files such as base.yml or a RadExpand backup (.json). For Espanso files you see a preview first; nothing is added without your approval. The original file is not changed."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { pickImport.launch(arrayOf("*/*")) }) { Text(t("Dosya seç (.yml / .json)…", "Choose file (.yml / .json)…")) }
            OutlinedButton(onClick = { val n = com.radexpand.ui.importSamplePack(Repo); say(t("Örnek paketten $n kısayol eklendi.", "$n shortcuts added from the sample pack.")) }) { Text(t("Örnek radyoloji paketi", "Sample radiology pack")) }
        }

        SectionTitle(t("Windows ile senkron", "Sync with Windows"))
        Hint(
            t(
                "Tablet ve Windows RadExpand aynı dosyayı kullanır. Dosyayı Google Drive (ya da Syncthing) klasörüne koyun; Windows'ta aynı dosyayı seçin. Aynı kısayol iki yerde değiştiyse son değişiklik kazanır; silmeler de taşınır. RadExpand'in internet izni yoktur; ağ işini Drive/Syncthing yapar. Dosyayı İndirilenler klasörüne koymayın.",
                "The tablet and Windows RadExpand use the same file. Put it in a Google Drive (or Syncthing) folder and pick the same file on Windows. If a shortcut changed in both places, the latest change wins; deletions are synced too. RadExpand has no internet permission; Drive/Syncthing does the network part. Do not use the Downloads folder.",
            ),
        )
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sync.syncUri == null) {
                    Text(t("Senkron dosyası bağlı değil.", "No sync file linked."))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { createSync.launch("radexpand-sync.json") }) { Text(t("Yeni senkron dosyası oluştur…", "Create a new sync file…")) }
                        OutlinedButton(onClick = { openSync.launch(arrayOf("*/*")) }) { Text(t("Var olan dosyayı seç…", "Choose an existing file…")) }
                    }
                } else {
                    Text(t("Bağlı dosya: ", "Linked file: ") + sync.syncName, fontWeight = FontWeight.Bold)
                    sync.lastSync?.let { Text(t("Son senkron: ", "Last sync: ") + it) }
                    sync.message?.let { Text(it, color = if (sync.error) MaterialTheme.colorScheme.error else Ok) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { SyncManager.syncNow() }) { Text(t("Şimdi senkronla", "Sync now")) }
                        OutlinedButton(onClick = { SyncManager.unlinkSync() }) { Text(t("Bağlantıyı kaldır", "Unlink")) }
                    }
                }
            }
        }

        SectionTitle(t("Espanso dosyasını otomatik güncelle (geçiş dönemi için)", "Keep an Espanso file updated (transition period)"))
        Hint(t("Bilgisayarda hâlâ Espanso kullanıyorsanız: seçtiğiniz .yml dosyasına her değişiklikte Espanso biçiminde yazılır.", "If you still use Espanso on a computer: every change is written to the chosen .yml file in Espanso format."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            if (sync.yamlUri == null) {
                OutlinedButton(onClick = { createYamlLink.launch("radexpand-espanso.yml") }) { Text(t("Espanso çıktı dosyası seç…", "Choose Espanso output file…")) }
            } else {
                Text(t("Yazılıyor: ", "Writing: ") + sync.yamlName, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { SyncManager.unlinkYaml() }) { Text(t("Durdur", "Stop")) }
            }
        }

        SectionTitle(t("Dışa aktar (yedek)", "Export (backup)"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportJson.launch("radexpand-yedek-${today()}.json") }) { Text(t("RadExpand (.json) — tam yedek", "RadExpand (.json) — full backup")) }
            OutlinedButton(onClick = { exportYaml.launch("radexpand-espanso-${today()}.yml") }) { Text("Espanso (.yml)") }
        }
        Hint(t("Tam yedek ayarları da içerir. Espanso biçimi \"yalnızca Tab\" modunu ve pasif kısayolları tam taşıyamaz.", "The full backup includes settings. The Espanso format cannot fully represent \"Tab only\" mode or inactive shortcuts."))

        SectionTitle(t("Otomatik yedekler", "Automatic backups"))
        Hint(t("Her gün ilk değişiklikte, her içe aktarmadan, geri yüklemeden ve senkrondan önce otomatik yedek alınır (son ${data.settings.maxBackups}).", "A backup is taken automatically at the first change of each day and before every import, restore and sync (last ${data.settings.maxBackups})."))
        val backups = remember(data, backupsTick) { Repo.backups() }
        if (backups.isEmpty()) Text(t("Henüz yedek yok.", "No backups yet."), modifier = Modifier.padding(top = 6.dp))
        backups.take(15).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name.removePrefix("radexpand-").removeSuffix(".json"), fontFamily = FontFamily.Monospace)
                    Hint(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified())) + " · " + Formatter.formatShortFileSize(ctx, f.length()))
                }
                TextButton(onClick = { restoreAsk = f }) { Text(t("Geri yükle", "Restore")) }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.padding(24.dp))
    }

    preview?.let { reps ->
        ImportPreviewDialog(reps, data, Repo, onDismiss = { preview = null }, onDone = { msg -> preview = null; backupsTick++; say(msg) })
    }

    restoreAsk?.let { f ->
        AlertDialog(
            onDismissRequest = { restoreAsk = null },
            title = { Text(t("Yedeği geri yükle?", "Restore this backup?")) },
            text = { Text(t("Kısayollarınız bu yedekteki hâline döner. Şimdiki hâliniz de ayrıca yedeklenir, yani bu işlem geri alınabilir.", "Your shortcuts return to this backup. The current state is backed up too, so this can be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { Repo.restoreBackup(f) }.onSuccess { say(t("Yedek geri yüklendi.", "Backup restored.")) }.onFailure { say(t("Geri yüklenemedi: ", "Could not restore: ") + it.message) }
                    restoreAsk = null
                    backupsTick++
                }) { Text(t("Geri yükle", "Restore")) }
            },
            dismissButton = { TextButton(onClick = { restoreAsk = null }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
}

