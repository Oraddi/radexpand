package com.radexpand.desktop

import com.radexpand.core.DataCodec
import com.radexpand.core.RadExpandData
import com.radexpand.core.ShortcutRecord
import com.radexpand.core.SyncMerge
import com.radexpand.core.tx
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Bilgisayarlar arasi ELLE tasima (Google Drive uygulamasi kurulamayan bilgisayarlar icin).
 * RadExpand internete cikmaz: kisayollar Indirilenler klasorune sabit adla yazilir, kullanici dosyayi
 * tarayicida Drive'a surukler; baska bilgisayarda indirip "Drive'dan al" ile birlestirir.
 */
object DriveShare {
    const val FILE_NAME = "radexpand-kisayollar.json"
    private const val DRIVE_URL = "https://drive.google.com/drive/my-drive"

    /** Testler icin: Indirilenler yerine kullanilacak klasor. */
    @Volatile
    var downloadsOverride: File? = null

    /** Windows'un Indirilenler klasoru (tasinmis olsa bile). */
    val downloads: File
        get() = downloadsOverride ?: systemDownloads

    private val systemDownloads: File by lazy {
        runCatching { File(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)) }.getOrNull()?.takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"), "Downloads")
    }

    /** Kisayol listesinin parmak izi (yalnizca kimlik, tarih, silinme; metin yok). */
    fun stamp(list: List<ShortcutRecord>): String {
        val s = list.sortedBy { it.id }.joinToString("\n") { "${it.id}:${it.updatedAt}:${it.deleted}" }
        return MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    /** Bu bilgisayarda Drive'a gonderilmemis degisiklik var mi (ozellik bir kez kullanildiysa). */
    fun isPending(data: RadExpandData, prefs: DesktopPrefs): Boolean =
        prefs.manualSyncUsed && prefs.lastSharedStamp != stamp(data.shortcuts)

    /**
     * Kisayollari Indirilenler'e yaz, dosyayi Gezgin'de secili goster ve Drive'i tarayicida ac.
     * @return kaydedilen dosya
     */
    fun send(openBrowser: Boolean = true, reveal: Boolean = true): File {
        val d = DesktopStore.data.value
        val out = File(downloads, FILE_NAME)
        val tmp = File(downloads, "$FILE_NAME.tmp")
        tmp.writeText(DataCodec.encode(RadExpandData(shortcuts = d.shortcuts)), Charsets.UTF_8)
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        DesktopStore.updatePrefs { it.copy(manualSyncUsed = true, lastSharedStamp = stamp(d.shortcuts)) }
        if (reveal) runCatching { ProcessBuilder("explorer.exe", "/select,", out.absolutePath).start() }
        if (openBrowser) runCatching { Desktop.getDesktop().browse(URI(DRIVE_URL)) }
        return out
    }

    /** Indirilenler'deki en yeni RadExpand dosyasi (tarayici ayni ada "(1)" ekleyebilir). */
    fun latestDownloaded(): File? =
        downloads.listFiles { f -> f.isFile && f.name.startsWith("radexpand-kisayollar") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }

    /** Drive'dan indirilen dosyayi birlestir. @return kullaniciya gosterilecek ileti */
    fun receive(f: File): String {
        val remote = DataCodec.decode(f.readText(Charsets.UTF_8))
        val r = SyncMerge.merge(DesktopStore.data.value.shortcuts, remote.shortcuts)
        if (r.changed) {
            DesktopStore.backup("drive-alma-oncesi")
            DesktopStore.update { it.copy(shortcuts = r.shortcuts) }
        }
        // Dosyadaki hal "gonderilmis" sayilir; bu bilgisayarda dosyada olmayan yenilikler varsa uyari kalir.
        DesktopStore.updatePrefs { it.copy(manualSyncUsed = true, lastSharedStamp = stamp(remote.shortcuts)) }
        val pendingNow = isPending(DesktopStore.data.value, DesktopStore.prefs.value)
        return tx("${r.fromRemote} kısayol alındı/güncellendi.", "${r.fromRemote} shortcuts received/updated.") +
            if (pendingNow) tx(" Bu bilgisayarda Drive'da olmayan değişiklikler de var: \"Drive'a gönder\" ile gönderin.", " This computer also has changes that are not in Drive: send them with \"Send to Drive\".") else ""
    }
}
