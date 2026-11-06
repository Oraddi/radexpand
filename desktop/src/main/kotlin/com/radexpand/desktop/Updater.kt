package com.radexpand.desktop

import com.radexpand.core.tx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Windows guncelleme (E3). Ayri bir hizmet YOK: calisan uygulama acilistan 1 dk sonra ve gunde bir kez
 * GitHub'a tek bir kucuk istek atar. Yeni surum varsa kullaniciya sorar; kendiliginden kurmaz.
 * Gonderilen tek bilgi standart bir web istegidir (yazilan metin, kisayol vb. ASLA gonderilmez).
 */
object Updater {
    const val REPO = "Oraddi/radexpand"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    data class State(
        val checking: Boolean = false,
        val latest: String? = null,
        val notes: String = "",
        val installerUrl: String? = null,
        val pageUrl: String = "https://github.com/$REPO/releases/latest",
        val message: String? = null,
        val downloading: Boolean = false,
        val progress: Float = 0f,
    ) {
        val updateAvailable: Boolean get() = latest != null && isNewer(latest, currentVersion)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state
    private val exec = Executors.newSingleThreadScheduledExecutor { Thread(it, "radexpand-update").apply { isDaemon = true } }
    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build() }

    /** Paketlenmis uygulamada jpackage verir; gelistirme sirasinda sabit. */
    val currentVersion: String get() = System.getProperty("jpackage.app-version") ?: System.getProperty("radexpand.version") ?: "0.0.0"

    /** Yeni surum bulununca cagrilir (tepsi bildirimi icin). */
    var onUpdateFound: ((String) -> Unit)? = null

    fun start() {
        exec.scheduleWithFixedDelay({
            if (DesktopStore.data.value.settings.checkUpdates) check(quiet = true)
        }, 1, 24 * 60, TimeUnit.MINUTES)
    }

    fun checkNow() = exec.execute { check(quiet = false) }

    private fun check(quiet: Boolean) {
        _state.value = _state.value.copy(checking = true, message = null)
        try {
            val req = HttpRequest.newBuilder(URI(API)).header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RadExpand-Updater").timeout(Duration.ofSeconds(15)).GET().build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() == 404) {
                _state.value = _state.value.copy(checking = false, message = tx("Henüz yayınlanmış bir sürüm yok.", "No release published yet."))
                return
            }
            if (res.statusCode() != 200) error("HTTP ${res.statusCode()}")
            val o = Json.parseToJsonElement(res.body()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: error("tag yok")
            val notes = o["body"]?.jsonPrimitive?.content.orEmpty()
            val page = o["html_url"]?.jsonPrimitive?.content ?: _state.value.pageUrl
            val msi = o["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".msi", ignoreCase = true) == true }
                ?.get("browser_download_url")?.jsonPrimitive?.content
            val newer = isNewer(tag, currentVersion)
            _state.value = _state.value.copy(
                checking = false, latest = tag, notes = notes, installerUrl = msi, pageUrl = page,
                message = if (newer) tx("Yeni sürüm var: $tag", "New version available: $tag") else tx("RadExpand güncel ($currentVersion).", "RadExpand is up to date ($currentVersion)."),
            )
            if (newer) onUpdateFound?.invoke(tag)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                checking = false,
                message = if (quiet) null else tx("Kontrol edilemedi (internet bağlantısı?): ", "Could not check (internet connection?): ") + (e.message ?: e.javaClass.simpleName),
            )
        }
    }

    /** Kurulum dosyasini indir, calistir ve uygulamayi kapat (kurulum yeni surumu baslatir). */
    fun downloadAndInstall(onExit: () -> Unit) = exec.execute {
        val url = _state.value.installerUrl ?: return@execute
        try {
            _state.value = _state.value.copy(downloading = true, progress = 0f, message = tx("İndiriliyor…", "Downloading…"))
            val file = File(System.getProperty("java.io.tmpdir"), "RadExpand-${_state.value.latest}.msi")
            val req = HttpRequest.newBuilder(URI(url)).header("User-Agent", "RadExpand-Updater").GET().build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (res.statusCode() != 200) error("HTTP ${res.statusCode()}")
            val total = res.headers().firstValueAsLong("Content-Length").orElse(-1L)
            res.body().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = _state.value.copy(progress = done.toFloat() / total)
                    }
                }
            }
            _state.value = _state.value.copy(downloading = false, message = tx("Kurulum başlatılıyor…", "Starting the installer…"))
            // msiexec: eski surumu kaldirip yenisini kurar (ayni upgradeUuid); veriler %APPDATA%'da kalir.
            ProcessBuilder("msiexec", "/i", file.absolutePath, "/passive").start()
            Thread.sleep(800)
            onExit()
        } catch (e: Exception) {
            _state.value = _state.value.copy(downloading = false, message = tx("Güncelleme başarısız: ", "Update failed: ") + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** "0.10.0" > "0.9.2" gibi surum karsilastirmasi. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
