@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.radexpand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radexpand.core.Accent
import com.radexpand.core.AppSettings
import com.radexpand.core.CaseMode
import com.radexpand.core.ConflictPolicy
import com.radexpand.core.KeyAction
import com.radexpand.core.NoWordImport
import com.radexpand.core.RadExpandData
import com.radexpand.core.ThemeMode
import com.radexpand.core.UndoKey

/** Platforma ozgu parcalar (Android / Windows). */
class PlatformSettings(
    val isWindows: Boolean,
    /** Duvar kagidi renkleri destekleniyor mu (Android 12+). */
    val supportsDynamicColor: Boolean,
    /**
     * Bir sonraki tusu yakala ("tusa basin"). Sonuc: (kod, ekranda gorunen ad) ya da null (iptal / atanamaz).
     * Platform, yakalama bitince [onResult]'i cagirir.
     */
    val startKeyCapture: (onResult: (String?, String) -> Unit) -> Unit,
    val cancelKeyCapture: () -> Unit,
    /** Ust kisimda (klavye kurulumu vb.) gosterilecek bolum. */
    val top: @Composable () -> Unit = {},
    /** Alt kisimda (Windows: baslangic, guncelleme; Android: klavye cikinca) gosterilecek bolum. */
    val bottom: @Composable () -> Unit = {},
)

@Composable
fun SettingsScreen(data: RadExpandData, store: DataStore, platform: PlatformSettings) {
    val s = data.settings
    fun set(f: (AppSettings) -> AppSettings) = store.update { it.copy(settings = f(it.settings)) }
    var capturing by remember { mutableStateOf<String?>(null) } // "special" | "pause"
    var askReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SwitchRow(
            t("RadExpand açık", "RadExpand on"),
            t("Kapalıyken hiçbir şey genişletmez; klavye normal yazar.", "When off, nothing expands; the keyboard types normally."),
            s.enabled,
        ) { v -> set { it.copy(enabled = v) } }
        platform.top()

        // ------------------------------------------------------------ dil ve gorunum
        SectionTitle(t("Dil ve görünüm", "Language and appearance"))
        ChoiceRow(t("Dil", "Language")) {
            FilterChip(UiLang.lang == "tr", { set { it.copy(language = "tr") }; UiLang.lang = "tr" }, { Text("Türkçe") })
            FilterChip(UiLang.lang == "en", { set { it.copy(language = "en") }; UiLang.lang = "en" }, { Text("English") })
        }
        ChoiceRow(t("Tema", "Theme")) {
            FilterChip(s.theme == ThemeMode.SYSTEM, { set { it.copy(theme = ThemeMode.SYSTEM) } }, { Text(t("Sistem", "System")) })
            FilterChip(s.theme == ThemeMode.LIGHT, { set { it.copy(theme = ThemeMode.LIGHT) } }, { Text(t("Açık", "Light")) })
            FilterChip(s.theme == ThemeMode.DARK, { set { it.copy(theme = ThemeMode.DARK) } }, { Text(t("Koyu", "Dark")) })
        }
        ChoiceRow(t("Renk", "Colour")) {
            Accent.entries.filter { it != Accent.DYNAMIC || platform.supportsDynamicColor }.forEach { a ->
                FilterChip(
                    s.accent == a, { set { it.copy(accent = a) } },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (a != Accent.DYNAMIC) Box(Modifier.size(14.dp).clip(CircleShape).background(accentColor(a)))
                            Text(
                                when (a) {
                                    Accent.TEAL -> t(" Camgöbeği", " Teal")
                                    Accent.BLUE -> t(" Mavi", " Blue")
                                    Accent.PURPLE -> t(" Mor", " Purple")
                                    Accent.ORANGE -> t(" Turuncu", " Orange")
                                    Accent.GREEN -> t(" Yeşil", " Green")
                                    Accent.DYNAMIC -> t("Duvar kâğıdı", "Wallpaper")
                                },
                            )
                        }
                    },
                )
            }
        }
        ChoiceRow(t("Yazı boyutu", "Text size")) {
            listOf(0.9f to t("Küçük", "Small"), 1.0f to t("Normal", "Normal"), 1.15f to t("Büyük", "Large"), 1.3f to t("Çok büyük", "Extra large")).forEach { (v, l) ->
                FilterChip(s.textScale == v, { set { it.copy(textScale = v) } }, { Text(l) })
            }
        }

        // ------------------------------------------------------------ tuslar
        SectionTitle(t("Genişletme tuşları", "Expansion keys"))
        Hint(t("Her tuş için seçin: kapalı, genişlet + tuşun kendi işi, ya da yalnızca genişlet.", "Choose for each key: off, expand + the key's own action, or expand only."))
        KeyRow("Space", t("boşluk", "space"), s.spaceAction) { v -> set { it.copy(spaceAction = v) } }
        KeyRow("Enter", t("yeni satır", "new line"), s.enterAction) { v -> set { it.copy(enterAction = v) } }
        KeyRow("Tab", t("sekme / sonraki alan", "tab / next field"), s.tabAction) { v -> set { it.copy(tabAction = v) } }
        Hint(t("Önerilen: Space ve Enter \"genişlet + kendi işi\", Tab \"yalnızca genişlet\" (ri + Tab + ler → hiperintensler).", "Recommended: Space and Enter \"expand + own action\", Tab \"expand only\" (to add a suffix right after the text)."))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Özel genişletme tuşu", "Special expansion key"), fontWeight = FontWeight.Bold)
                Hint(
                    t(
                        "İstediğiniz bir tuşu ya da kombinasyonu (ör. F2, Ctrl+Space) genişletme tuşu yapın. Yalnızca genişletir; tuşun başka işi yapılmaz. \"Yalnızca Tab\" kısayolları da bu tuşla genişler.",
                        "Make any key or combination (e.g. F2, Ctrl+Space) an expansion key. It only expands. \"Tab only\" shortcuts also expand with it.",
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (s.specialKey == null) t("Atanmadı", "Not assigned") else s.specialKeyLabel, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        capturing = "special"
                        platform.startKeyCapture { code, label ->
                            capturing = null
                            if (code != null) set { it.copy(specialKey = code, specialKeyLabel = label) }
                        }
                    }) { Text(t("Tuş ata", "Assign key")) }
                    if (s.specialKey != null) TextButton(onClick = { set { it.copy(specialKey = null, specialKeyLabel = "") } }) { Text(t("Kaldır", "Remove")) }
                }
                SwitchRow(
                    t("\"Yalnızca Tab\" kısayolları Tab ile de genişlesin", "\"Tab only\" shortcuts also expand with Tab"),
                    t("Kapalıysa yalnızca özel tuşla genişlerler.", "When off, they expand only with the special key."),
                    s.tabForSpecialOnly,
                ) { v -> set { it.copy(tabForSpecialOnly = v) } }
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SwitchRow(
                    t("Kısayol arama paneli", "Shortcut search panel"),
                    t(
                        "Kısayol tuşuna basınca kayıtlı kısayollar listelenir (Espanso'daki gibi): yazarak arayın, ↑↓ ile seçin, Enter ile yazdırın, Esc ile kapatın.",
                        "Press the key to list your saved shortcuts (like Espanso): type to search, ↑↓ to select, Enter to insert, Esc to close.",
                    ),
                    s.searchKeyEnabled,
                ) { v -> set { it.copy(searchKeyEnabled = v) } }
                if (s.searchKeyEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.searchCustomKey == null) "Alt + Space" else s.searchCustomKeyLabel, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            capturing = "search"
                            platform.startKeyCapture { code, label ->
                                capturing = null
                                if (code != null) set { it.copy(searchCustomKey = code, searchCustomKeyLabel = label) }
                            }
                        }) { Text(t("Başka tuş ata", "Assign another key")) }
                        if (s.searchCustomKey != null) TextButton(onClick = { set { it.copy(searchCustomKey = null, searchCustomKeyLabel = "") } }) { Text(t("Alt + Space'e dön", "Back to Alt + Space")) }
                    }
                }
            }
        }

        if (!platform.isWindows) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Ekran klavyesine geçiş tuşu", "Switch-to-screen-keyboard key"), fontWeight = FontWeight.Bold)
                    Hint(
                        t(
                            "Fiziksel klavyedeyken bu tuşa basınca ekran klavyesine (Samsung Klavye vb.) geçilir; seçim penceresi açılmaz. Ekranda görünmesi için Android'de \"Fiziksel klavye → Ekran klavyesini göster\" açık olmalı.",
                            "While on the physical keyboard, this key switches to the on-screen keyboard (Samsung Keyboard etc.) without the picker. For it to appear, Android's \"Physical keyboard → Show on-screen keyboard\" must be on.",
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.screenKeyboardKey == null) t("Atanmadı", "Not assigned") else s.screenKeyboardKeyLabel, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            capturing = "screen"
                            platform.startKeyCapture { code, label ->
                                capturing = null
                                if (code != null) set { it.copy(screenKeyboardKey = code, screenKeyboardKeyLabel = label) }
                            }
                        }) { Text(t("Tuş ata", "Assign key")) }
                        if (s.screenKeyboardKey != null) TextButton(onClick = { set { it.copy(screenKeyboardKey = null, screenKeyboardKeyLabel = "") } }) { Text(t("Kaldır", "Remove")) }
                    }
                }
            }
        }

        // ------------------------------------------------------------ kelime davranislari
        SectionTitle(t("Kelime davranışları", "Word behaviour"))
        SwitchRow(
            t("Kesme işareti kelimeye dahil", "Apostrophe is part of the word"),
            t("Açıkken \"MR'dan\" içindeki \"dan\" genişlemez (Türkçe ekler için önerilir).", "When on, a shortcut right after an apostrophe does not expand (recommended for Turkish suffixes)."),
            s.apostropheIsWord,
        ) { v -> set { it.copy(apostropheIsWord = v) } }
        SwitchRow(
            t("Tire kelimeye dahil", "Hyphen is part of the word"),
            t("Açıkken \"non-sp\" içindeki \"sp\" genişlemez.", "When on, a shortcut right after a hyphen does not expand."),
            s.hyphenIsWord,
        ) { v -> set { it.copy(hyphenIsWord = v) } }
        SwitchRow(
            t("Eğik çizgi kelimeye dahil", "Slash is part of the word"),
            t("Açıkken \"T2/ri\" içindeki \"ri\" genişlemez.", "When on, a shortcut right after a slash does not expand."),
            s.slashIsWord,
        ) { v -> set { it.copy(slashIsWord = v) } }
        ChoiceRow(t("Kısayol büyük harfle yazılınca", "When a shortcut is typed with capitals")) {
            FilterChip(s.caseMode == CaseMode.OFF, { set { it.copy(caseMode = CaseMode.OFF) } }, { Text(t("Genişlemesin", "Don't expand")) })
            FilterChip(s.caseMode == CaseMode.KEEP_TEXT, { set { it.copy(caseMode = CaseMode.KEEP_TEXT) } }, { Text(t("Metin olduğu gibi", "Text as saved")) })
            FilterChip(s.caseMode == CaseMode.CAPITAL, { set { it.copy(caseMode = CaseMode.CAPITAL) } }, { Text(t("İlk harf büyük", "First letter capital")) })
            FilterChip(s.caseMode == CaseMode.FULL, { set { it.copy(caseMode = CaseMode.FULL) } }, { Text(t("İlk harf + tamamı büyük", "First letter + all caps")) })
        }
        Hint(
            when (s.caseMode) {
                CaseMode.OFF -> t("\"Ard\" yazınca genişlemez; yalnızca küçük harfle yazılan kısayol genişler.", "\"Thx\" does not expand; only the lowercase shortcut does.")
                CaseMode.KEEP_TEXT -> t("\"Ard\" → \"ardından\": metin kaydettiğiniz gibi yazılır.", "\"Thx\" → \"thank you\": the text is typed as you saved it.")
                CaseMode.CAPITAL -> t("\"Ard\" → \"Ardından\". Tamamı büyük (\"ARD\") genişlemez.", "\"Thx\" → \"Thank you\". All caps (\"THX\") does not expand.")
                CaseMode.FULL -> t("\"Ard\" → \"Ardından\", \"ARD\" → \"ARDINDAN\".", "\"Thx\" → \"Thank you\", \"THX\" → \"THANK YOU\".")
            },
        )
        if (s.caseMode == CaseMode.FULL) {
            ChoiceRow(t("Tamamı büyük için en az harf", "Minimum letters for all caps")) {
                (2..5).forEach { n -> FilterChip(s.minUpperLetters == n, { set { it.copy(minUpperLetters = n) } }, { Text("$n") }) }
            }
            Hint(t("3 önerilir: \"CC\", \"ML\" gibi iki harfli kısaltmalar bozulmaz.", "3 is recommended: two-letter abbreviations like \"CC\", \"ML\" are kept."))
        }
        SwitchRow(
            t("Yeni kısayollarda büyük harfi koru", "Keep capitalization for new shortcuts"),
            t("Yeni eklenen kısayolların varsayılanı.", "Default for newly added shortcuts."),
            s.defaultPropagateCase,
        ) { v -> set { it.copy(defaultPropagateCase = v) } }
        SwitchRow(
            t("Noktalama ile de genişlet", "Also expand with punctuation"),
            t("\"mev,\" → \"mevcut,\" — işaret metnin arkasına eklenir.", "\"ri,\" → \"hyperintense,\" — the mark is added after the text."),
            s.punctuationTriggers,
        ) { v -> set { it.copy(punctuationTriggers = v) } }
        if (s.punctuationTriggers) {
            OutlinedTextField(
                s.punctuationChars, { v -> set { it.copy(punctuationChars = v.filter { c -> !c.isLetterOrDigit() && !c.isWhitespace() }.take(12)) } },
                label = { Text(t("Hangi işaretler", "Which marks")) }, singleLine = true, modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        UndoKeyCard(s, platform, onCapture = { capturing = it }) { f -> set(f) }

        // ------------------------------------------------------------ ice aktarma, yedek, senkron
        SectionTitle(t("İçe aktarma, yedek ve senkron", "Import, backup and sync"))
        ChoiceRow(t("Espanso'da \"word\" olmayan kısayollar", "Espanso shortcuts without \"word\"")) {
            FilterChip(s.espansoNoWordMode == NoWordImport.AUTO_WORD_START, { set { it.copy(espansoNoWordMode = NoWordImport.AUTO_WORD_START) } }, { Text(t("Otomatik, kelime başında", "Automatic, at word start")) })
            FilterChip(s.espansoNoWordMode == NoWordImport.AUTO_ANYWHERE, { set { it.copy(espansoNoWordMode = NoWordImport.AUTO_ANYWHERE) } }, { Text(t("Otomatik, her yerde (Espanso gibi)", "Automatic, anywhere (like Espanso)")) })
            FilterChip(s.espansoNoWordMode == NoWordImport.MANUAL, { set { it.copy(espansoNoWordMode = NoWordImport.MANUAL) } }, { Text(t("Space/Enter/Tab ile", "With Space/Enter/Tab")) })
        }
        ChoiceRow(t("Çakışmada varsayılan", "Default on conflict")) {
            FilterChip(s.defaultConflictPolicy == ConflictPolicy.KEEP_EXISTING, { set { it.copy(defaultConflictPolicy = ConflictPolicy.KEEP_EXISTING) } }, { Text(t("Mevcut kalsın", "Keep existing")) })
            FilterChip(s.defaultConflictPolicy == ConflictPolicy.REPLACE, { set { it.copy(defaultConflictPolicy = ConflictPolicy.REPLACE) } }, { Text(t("Gelenle değiştir", "Replace")) })
            FilterChip(s.defaultConflictPolicy == ConflictPolicy.KEEP_BOTH, { set { it.copy(defaultConflictPolicy = ConflictPolicy.KEEP_BOTH) } }, { Text(t("İkisini de tut", "Keep both")) })
        }
        ChoiceRow(t("Saklanacak otomatik yedek", "Automatic backups to keep")) {
            listOf(10, 30, 60, 100).forEach { n -> FilterChip(s.maxBackups == n, { set { it.copy(maxBackups = n) } }, { Text("$n") }) }
        }
        SwitchRow(
            t("Espanso'ya aktarırken pasifleri de yaz", "Include inactive shortcuts in Espanso export"),
            t("Yorum satırı olarak yazılır; Espanso onları kullanmaz.", "Written as comments; Espanso does not use them."),
            s.exportDisabled,
        ) { v -> set { it.copy(exportDisabled = v) } }
        if (!platform.isWindows) {
            ChoiceRow(t("Senkron dosyasını kontrol et", "Check the sync file")) {
                listOf(1 to t("1 dk", "1 min"), 5 to t("5 dk", "5 min"), 15 to t("15 dk", "15 min"), 60 to t("1 saat", "1 hour")).forEach { (n, l) ->
                    FilterChip(s.syncCheckMinutes == n, { set { it.copy(syncCheckMinutes = n) } }, { Text(l) })
                }
            }
        }
        SwitchRow(t("Senkrondan önce yedek al", "Back up before syncing"), "", s.backupBeforeSync) { v -> set { it.copy(backupBeforeSync = v) } }

        // ------------------------------------------------------------ gizlilik
        SectionTitle(t("Güvenlik ve gizlilik", "Security and privacy"))
        if (!platform.isWindows) {
            SwitchRow(
                t("Parola alanlarında da genişlet", "Expand in password fields too"),
                t("Önerilmez. Kapalıyken parola alanlarında RadExpand hiçbir tuşu işlemez ve hiçbir şey tutmaz.", "Not recommended. When off, RadExpand ignores password fields completely."),
                s.expandInPasswordFields,
            ) { v -> set { it.copy(expandInPasswordFields = v) } }
            SwitchRow(
                t("Kör mod", "Blind mode"),
                t("Bir uygulama imleç öncesi metni vermezse, RadExpand yalnızca kendi gördüğü tuşlara göre genişletir (kelime başı kesin değilse genişletmez).", "If an app does not report the text before the cursor, RadExpand uses only the keys it saw (and does not expand if unsure)."),
                s.allowBlindMode,
            ) { v -> set { it.copy(allowBlindMode = v) } }
        }
        SwitchRow(
            t("Tanılama kaydı", "Diagnostic log"),
            t("Sorun çözmek için tuş türleri ve süreler bellekte tutulur. Yazdığınız metin hiçbir zaman kaydedilmez.", "Key types and timings are kept in memory for troubleshooting. What you type is never recorded."),
            s.diagnosticsEnabled,
        ) { v -> set { it.copy(diagnosticsEnabled = v) } }

        platform.bottom()

        SectionTitle(t("Sıfırlama", "Reset"))
        Hint(t("Ayarlar tam yedeğin (.json) içinde de saklanır.", "Settings are also stored in the full backup (.json)."))
        OutlinedButton(onClick = { askReset = true }) { Text(t("Ayarları varsayılana döndür", "Reset settings to defaults")) }
        Spacer(Modifier.padding(24.dp))
    }

    if (capturing != null) {
        AlertDialog(
            onDismissRequest = { platform.cancelKeyCapture(); capturing = null },
            title = { Text(t("Tuşa basın", "Press a key")) },
            text = { Text(t("Atamak istediğiniz tuşa ya da kombinasyona (ör. F2, Ctrl+Space) fiziksel klavyede basın. Harf, rakam, Space, Enter, Tab ve Backspace tek başına atanamaz.", "Press the key or combination you want (e.g. F2, Ctrl+Space). Letters, digits, Space, Enter, Tab and Backspace cannot be used alone.")) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { platform.cancelKeyCapture(); capturing = null }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text(t("Ayarlar sıfırlansın mı?", "Reset settings?")) },
            text = { Text(t("Kısayollarınız değişmez. Dil ve uygulama bazında kapatma listesi korunur.", "Your shortcuts are not changed. Language and per-app lists are kept.")) },
            confirmButton = {
                TextButton(onClick = {
                    set { AppSettings(language = it.language, onboardingDone = true, disabledPackages = it.disabledPackages, blindPackages = it.blindPackages, unknownStartIsBoundary = platform.isWindows) }
                    askReset = false
                }) { Text(t("Sıfırla", "Reset")) }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
}

@Composable
fun ChoiceRow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun KeyRow(key: String, ownAction: String, value: KeyAction, onChange: (KeyAction) -> Unit) {
    ChoiceRow(key) {
        FilterChip(value == KeyAction.OFF, { onChange(KeyAction.OFF) }, { Text(t("Kapalı", "Off")) })
        FilterChip(value == KeyAction.EXPAND_WITH_KEY, { onChange(KeyAction.EXPAND_WITH_KEY) }, { Text(t("Genişlet + ", "Expand + ") + ownAction) })
        FilterChip(value == KeyAction.EXPAND_ONLY, { onChange(KeyAction.EXPAND_ONLY) }, { Text(t("Yalnızca genişlet", "Expand only")) })
    }
}

/** Genisletmeyi geri alma tusu secimi. Backspace varsayilan olarak normal siler. */
@Composable
private fun UndoKeyCard(s: AppSettings, platform: PlatformSettings, onCapture: (String?) -> Unit, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Genişletmeyi geri alma", "Undo an expansion"), fontWeight = FontWeight.Bold)
            Hint(
                t(
                    "Genişletmeden hemen sonra bu tuşa basarsanız yazdığınız kısa hâl geri gelir (rapor → rp). Başka bir zaman basarsanız tuş normal işini yapar. Backspace her zaman normal siler (\"Backspace\" seçeneği hariç).",
                    "Press this key right after an expansion to get back what you typed (report → rpt). At any other time the key does its normal job. Backspace always deletes normally (except with the \"Backspace\" option).",
                ),
            )
            ChoiceRow(t("Geri alma tuşu", "Undo key")) {
                FilterChip(s.undoKey == UndoKey.SHIFT_BACKSPACE, { set { it.copy(undoKey = UndoKey.SHIFT_BACKSPACE) } }, { Text("Shift + Backspace") })
                FilterChip(s.undoKey == UndoKey.CUSTOM, {
                    if (s.undoCustomKey != null) set { it.copy(undoKey = UndoKey.CUSTOM) } else {
                        onCapture("undo")
                        platform.startKeyCapture { code, label ->
                            onCapture(null)
                            if (code != null) set { it.copy(undoKey = UndoKey.CUSTOM, undoCustomKey = code, undoCustomKeyLabel = label) }
                        }
                    }
                }, { Text(if (s.undoCustomKey != null) t("Özel: ", "Custom: ") + s.undoCustomKeyLabel else t("Özel tuş…", "Custom key…")) })
                FilterChip(s.undoKey == UndoKey.BACKSPACE, { set { it.copy(undoKey = UndoKey.BACKSPACE) } }, { Text("Backspace") })
                FilterChip(s.undoKey == UndoKey.OFF, { set { it.copy(undoKey = UndoKey.OFF) } }, { Text(t("Kapalı", "Off")) })
            }
            if (s.undoKey == UndoKey.CUSTOM) {
                TextButton(onClick = {
                    onCapture("undo")
                    platform.startKeyCapture { code, label ->
                        onCapture(null)
                        if (code != null) set { it.copy(undoKey = UndoKey.CUSTOM, undoCustomKey = code, undoCustomKeyLabel = label) }
                    }
                }) { Text(t("Başka tuş ata", "Assign another key")) }
            }
            Hint(
                when (s.undoKey) {
                    UndoKey.SHIFT_BACKSPACE -> t("Önerilen. Word, Google Dokümanlar ve tarayıcılarda Shift+Backspace normal silme gibi çalışır; Ctrl+Z (programın kendi geri alması) ile de çakışmaz.", "Recommended. In Word, Google Docs and browsers Shift+Backspace works like a normal delete, and it does not clash with Ctrl+Z (the program's own undo).")
                    UndoKey.CUSTOM -> t("Programların kendi kısayollarıyla çakışmayan bir tuş seçin (ör. F4). Ctrl+Z gibi tuşları seçmeyin.", "Choose a key that programs do not use themselves (e.g. F4). Avoid keys like Ctrl+Z.")
                    UndoKey.BACKSPACE -> t("Genişletmeden hemen sonraki ilk Backspace silmez, kısayolu geri getirir.", "The first Backspace right after an expansion does not delete; it brings the shortcut back.")
                    UndoKey.OFF -> t("Geri alma yok. Yanlış genişlemeyi elle silmeniz gerekir.", "No undo. You have to delete a wrong expansion yourself.")
                },
            )
        }
    }
}
