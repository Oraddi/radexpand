package com.radexpand.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radexpand.core.RadExpandData
import com.radexpand.desktop.DesktopStore
import com.radexpand.desktop.DriveShare
import com.radexpand.ui.Hint
import com.radexpand.ui.Ok
import com.radexpand.ui.SectionTitle
import com.radexpand.ui.t
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** "Drive'dan al": Indirilenler'deki en yeni RadExpand dosyasi secili hazir gelir. */
fun receiveFromDrive(say: (String) -> Unit) {
    val latest = DriveShare.latestDownloaded()
    val fd = FileDialog(null as Frame?, t("Drive'dan indirdiğiniz RadExpand dosyasını seçin", "Choose the RadExpand file you downloaded from Drive"), FileDialog.LOAD)
    fd.directory = (latest?.parentFile ?: DriveShare.downloads).absolutePath
    fd.file = latest?.name ?: "*.json"
    fd.isVisible = true
    val name = fd.file ?: return
    val dir = fd.directory ?: return
    runCatching { DriveShare.receive(File(dir, name)) }
        .onSuccess { say(it) }
        .onFailure { say(t("Bu dosya okunamadı ya da bir RadExpand dosyası değil.", "This file could not be read or is not a RadExpand file.")) }
}

/** "Drive'a gonder": dosyayi Indirilenler'e yaz, Gezgin'de goster, Drive'i ac. */
fun sendToDrive(say: (String) -> Unit) {
    runCatching { DriveShare.send() }
        .onSuccess { f -> say(t("Kaydedildi: ${f.name}. Şimdi tarayıcıda Drive'daki RadExpand klasörünüze sürükleyin.", "Saved: ${f.name}. Now drag it into your RadExpand folder in Drive.")) }
        .onFailure { e -> say(t("Kaydedilemedi: ", "Could not save: ") + e.message) }
}

/** Bilgisayarlar arasi elle tasima (RadExpand internete cikmaz). */
@Composable
fun DriveShareCard(data: RadExpandData, say: (String) -> Unit) {
    val prefs by DesktopStore.prefs.collectAsState()
    val pending = DriveShare.isPending(data, prefs)
    val file = DriveShare.FILE_NAME
    SectionTitle(t("Bilgisayarlar arasında taşı (Google Drive, elle)", "Move between computers (Google Drive, by hand)"))
    Hint(
        t(
            "Drive programı kurulamayan bilgisayarlar için. \"Drive'a gönder\": kısayollarınız İndirilenler klasörüne $file adıyla kaydedilir, dosya seçili gösterilir ve tarayıcıda Drive açılır. Dosyayı Drive'daki RadExpand klasörünüze sürükleyin (aynı ad varsa \"Mevcut dosyayı değiştir\"). Başka bilgisayarda dosyayı Drive'dan indirip \"Drive'dan al\" deyin. Kısayollar birleştirilir: daha yeni olan kalır, hiçbir şey çoğalmaz.",
            "For computers where the Drive program cannot be installed. \"Send to Drive\" saves your shortcuts to the Downloads folder as $file, shows the file selected and opens Drive in the browser. Drag the file into your RadExpand folder in Drive (if the name exists, choose \"Replace existing file\"). On another computer, download the file from Drive and press \"Get from Drive\". Shortcuts are merged: the newer one wins, nothing is duplicated.",
        ),
    )
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pending) {
                Text(t("⚠ Bu bilgisayardaki son değişiklikler henüz Drive'a gönderilmedi.", "⚠ The latest changes on this computer have not been sent to Drive yet."), color = MaterialTheme.colorScheme.error)
            } else if (prefs.manualSyncUsed) {
                Text(t("✓ Gönderilmemiş değişiklik yok.", "✓ No unsent changes."), color = Ok)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { sendToDrive(say) }) { Text(t("Drive'a gönder", "Send to Drive")) }
                OutlinedButton(onClick = { receiveFromDrive(say) }) { Text(t("Drive'dan al…", "Get from Drive…")) }
            }
            Hint(t("Drive'a tarayıcıdan girilir: drive.google.com. RadExpand'in kendisi internete bağlanmaz.", "Drive is opened in the browser: drive.google.com. RadExpand itself does not connect to the internet."))
        }
    }
}
