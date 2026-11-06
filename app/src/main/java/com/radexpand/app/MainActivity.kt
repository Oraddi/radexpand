package com.radexpand.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radexpand.app.ui.AndroidSettings
import com.radexpand.app.ui.AndroidTheme
import com.radexpand.app.ui.CompatScreen
import com.radexpand.app.ui.KeyCapture
import com.radexpand.app.ui.KeyboardSetupCard
import com.radexpand.app.ui.TransferScreen
import com.radexpand.app.ui.UiTicker
import com.radexpand.core.Lang
import com.radexpand.ui.GuideScreen
import com.radexpand.ui.Onboarding
import com.radexpand.ui.ShortcutsScreen
import com.radexpand.ui.UiLang
import com.radexpand.ui.importSamplePack
import com.radexpand.ui.t
import kotlinx.coroutines.launch

private enum class Tab { SHORTCUTS, TRANSFER, COMPAT, SETTINGS, GUIDE }

/** Klavye servisi ayni surecte: ana ekran onundeyken (kisayol duzenlerken) genisletme yapilmaz. */
object MainUiState {
    @Volatile
    var visible = false
}

/** Ana ekran: kisayol yonetimi, ice/disa aktarma, uyumluluk, ayarlar, rehber. */
class MainActivity : ComponentActivity() {

    private var pending by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        SyncManager.init(this)
        CompatStats.init(this)
        UiLang.lang = Lang.defaultFor(Repo.data.value.settings.language)
        handleIntent(intent)
        setContent {
            val data by Repo.data.collectAsState()
            AndroidTheme(data.settings) {
                AppRoot(pending, onPendingConsumed = { pending = emptyList() }, openTests = {
                    startActivity(Intent(this, TestActivity::class.java))
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Paylas -> RadExpand" ya da dosya yoneticisinden .yml/.json acma. */
    private fun handleIntent(i: Intent?) {
        i ?: return
        val uris = when (i.action) {
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM),
            )
            else -> emptyList()
        }
        if (uris.isNotEmpty()) pending = uris
    }

    /** Ayarlarda "Tus ata" icin: bir sonraki fiziksel tusu yakala. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = KeyCapture.handle(event) || super.dispatchKeyEvent(event)

    override fun onResume() {
        super.onResume()
        MainUiState.visible = true
        UiTicker.bump()
        SyncManager.syncIfStale()
    }

    override fun onPause() {
        MainUiState.visible = false
        KeyCapture.pending = null
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiTicker.bump()
    }
}

@Composable
private fun AppRoot(pending: List<Uri>, onPendingConsumed: () -> Unit, openTests: () -> Unit) {
    val data by Repo.data.collectAsState()
    val usage by CompatStats.usage.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.SHORTCUTS) }
    var pickRequest by remember { mutableStateOf(false) }
    if (pending.isNotEmpty() && tab != Tab.TRANSFER) tab = Tab.TRANSFER
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun sample() {
        val n = importSamplePack(Repo)
        scope.launch { snackbar.showSnackbar(t("Örnek paketten $n kısayol eklendi.", "$n shortcuts added from the sample pack.")) }
    }

    if (!data.settings.onboardingDone) {
        Onboarding(
            Repo,
            setupStep = {
                Text(t("RadExpand bir klavye uygulamasıdır: fiziksel klavyeyle yazarken kısayolları o genişletir. Aşağıdaki iki adımı tamamlayın.", "RadExpand is a keyboard app: it expands shortcuts while you type on a physical keyboard. Complete the two steps below."))
                KeyboardSetupCard(alwaysShow = true)
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
                navItem(tab == Tab.SHORTCUTS, Icons.Default.List, t("Kısayollar", "Shortcuts")) { tab = Tab.SHORTCUTS }
                navItem(tab == Tab.TRANSFER, Icons.Default.Share, t("Aktar", "Import")) { tab = Tab.TRANSFER }
                navItem(tab == Tab.COMPAT, Icons.Default.CheckCircle, t("Uyumluluk", "Compatibility")) { tab = Tab.COMPAT }
                navItem(tab == Tab.SETTINGS, Icons.Default.Settings, t("Ayarlar", "Settings")) { tab = Tab.SETTINGS }
                navItem(tab == Tab.GUIDE, Icons.Default.Info, t("Nasıl kullanılır", "How to use")) { tab = Tab.GUIDE }
                Spacer(Modifier.weight(1f))
                navItem(false, Icons.Default.Build, t("Test", "Test"), openTests)
            }
            Column(Modifier.weight(1f)) {
                if (tab != Tab.SETTINGS) Column(Modifier.padding(horizontal = 12.dp)) { KeyboardSetupCard(alwaysShow = false) }
                Repo.lastError?.let { Text("⚠ $it", modifier = Modifier.padding(12.dp)) }
                when (tab) {
                    Tab.SHORTCUTS -> ShortcutsScreen(data, snackbar, Repo, usage)
                    Tab.TRANSFER -> TransferScreen(data, snackbar, pending, onPendingConsumed, pickRequest) { pickRequest = false }
                    Tab.COMPAT -> CompatScreen(data)
                    Tab.SETTINGS -> AndroidSettings(data)
                    Tab.GUIDE -> GuideScreen(isWindows = false, onTryIt = openTests, onImportSample = { sample() })
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.navItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) =
    NavigationRailItem(selected = selected, onClick = onClick, icon = { Icon(icon, null) }, label = { Text(label) })
