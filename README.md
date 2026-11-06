# RadExpand

**[Türkçe](#türkçe) · [English](#english)**

Fiziksel klavye için çevrimdışı metin genişletici: Android tablet + Windows. Radyoloji raporları için yapıldı, her türlü metinde çalışır.
*Offline text expander for physical keyboards: Android tablets + Windows. Built for radiology reports, works for any text.*

[![Son sürüm / Latest release](https://img.shields.io/github/v/release/Oraddi/radexpand?label=release)](https://github.com/Oraddi/radexpand/releases/latest)
[![Lisans / License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

<p>
<img src="docs/screenshots/android-shortcuts.png" width="49%" alt="Kısayollar / Shortcuts">
<img src="docs/screenshots/android-guide.png" width="49%" alt="Nasıl kullanılır / How to use">
</p>

---

## Türkçe

### Ne yapar?
Kısa bir harf grubu yazıp bir tuşa basarsınız, uzun metne dönüşür:

| Yazdığınız | Sonuç |
|---|---|
| `ri` + **Space** | `hiperintens␣` |
| `ri` + **Enter** | `hiperintens` + yeni satır |
| `ri` + **Tab** + `ler` | `hiperintensler` (Tab hiçbir şey eklemez, imleç metnin sonunda kalır) |
| `birinci` + Space | `birinci␣`: kelimenin içindeki `ri` **genişlemez** |
| `Ri` + Space | `Hiperintens␣`: büyük harf korunur |
| genişlemeden hemen sonra **Backspace** | yazdığınız kısayol geri gelir |

- **Tamamen çevrimdışı.** Android uygulamasının internet izni yoktur; yazdıklarınız cihazdan çıkamaz.
- **Espanso** dosyalarını (`base.yml`) önizlemeyle içe aktarır ve Espanso biçiminde dışa aktarır.
- **Tablet ↔ Windows senkronu** ortak bir dosya üzerinden yapılır (ör. Google Drive klasörü). Son değişiklik kazanır, silmeler de taşınır.
- **Şablonlar:** `$|$` imlecin duracağı yer, birden fazla `$|$` varsa Tab ile sonrakine geçilir. `{tarih}` ve `{saat}` değişkenleri desteklenir.
- **Her şey Ayarlar'dan değiştirilebilir:**
  - Space / Enter / Tab'ın görevi ve özel genişletme tuşu (ör. F2);
  - kelime kuralları, büyük harf;
  - tema, renk, yazı boyutu;
  - yedekler, uygulama bazında kapatma.
- Uygulamanın içinde kısa bir **"Nasıl kullanılır"** rehberi ve isteğe bağlı **örnek radyoloji paketi** vardır.
- Arayüz Türkçe ve İngilizcedir; dil ilk açılışta sorulur.

### İndirme
[**Son sürüm sayfası →**](https://github.com/Oraddi/radexpand/releases/latest)

| Dosya | Ne için |
|---|---|
| `RadExpand-x.y.z.apk` | Android 8+ tablet/telefon (fiziksel klavyeyle) |
| `RadExpand-x.y.z.msi` | Windows 10/11 kurulum dosyası (yönetici izni gerekmez) |
| `RadExpand-Windows-x.y.z.zip` | Windows taşınabilir sürüm (kurulumsuz) |

### Android kurulumu
1. APK'yı tablette açın → **Yükle**. "Bilinmeyen uygulama" izni sorulursa bu kaynağa izin verin.
2. RadExpand'i açın, dili seçin. Kurulum ekranındaki iki adımı yapın: **klavye ayarlarında RadExpand'i açın** → **klavye olarak RadExpand'i seçin**.
   Android her klavye için "yazdıklarınızı toplayabilir" uyarısı gösterir; RadExpand'in internet izni yoktur.
3. Türkçe karakterler yanlış çıkarsa: **Ayarlar → Fiziksel klavye düzeni → Türkçe Q**.
4. Fiziksel klavye bağlı değilken ekranda küçük bir çubuk görünür; oradan normal klavyenize geçebilirsiniz.

**Otomatik güncelleme (isteğe bağlı):** Uygulamanın kendisi internete çıkmaz. Yeni sürümleri otomatik almak için [Obtainium](https://obtainium.imranr.dev/) uygulamasına bu sayfanın adresini (`https://github.com/Oraddi/radexpand`) ekleyin. Yeni sürüm yayımlandığında Obtainium haber verir. Güncellemeler aynı imzayla eskisinin üzerine kurulur, kısayollarınız kalır.

### Windows kurulumu
1. `.msi` dosyasını çalıştırın. SmartScreen "tanınmayan uygulama" derse **Ek bilgi → Yine de çalıştır** seçin (uygulama ücretli bir kod imzası taşımaz).
2. RadExpand sistem tepsisinde çalışır. Pencereyi kapatınca arka planda devam eder; sağ tık menüsünde Aç / Duraklat / Çıkış vardır.
3. **Espanso kullanıyorsanız kapatın.** İkisi birlikte çalışırsa kısayollar iki kez genişler (RadExpand bunu fark edip uyarır).
4. **Güncelleme:** Açılışta ve günde bir kez GitHub'a tek bir küçük istek gönderilir; yazdığınız hiçbir şey gönderilmez. Yeni sürüm varsa **Ayarlar → Güncelleme → İndir ve kur** çıkar. Sormadan kurmaz, istenirse kontrol tamamen kapatılabilir.

### Espanso'dan geçiş
**Aktar → Dosya seç** → `base.yml` (Windows'ta `%APPDATA%\espanso\match` klasörü kendiliğinden açılır).
Önizlemede kaç kısayolun geleceğini, uyarıları ve çakışmaları görürsünüz. Aynı dosyayı tekrar aktarmak kayıtları çoğaltmaz, orijinal dosya da değişmez.

### Sınırlamalar
- Yalnızca **fiziksel klavye** ile çalışır; ekran klavyesiyle yazarken genişletme yapılmaz.
- Windows'ta **yönetici olarak çalışan** programlarda (bazı RIS/PACS istemcileri) RadExpand'in de yönetici olarak çalıştırılması gerekir.
- Windows parola alanlarını genel olarak ayırt edemez; parola yöneticinizi **Ayarlar → Uygulama bazında kapatma**'dan kapatın. Android'de parola alanlarında genişletme varsayılan olarak kapalıdır.
- **Test durumu:** Android 15 tablet emülatöründe (Chrome dahil) ve Windows 11'de otomatik testlerle denendi. Gerçek Samsung tablet ve Google Dokümanlar ile henüz geniş kullanım deneyimi yoktur. Sorun görürseniz [bildirin](https://github.com/Oraddi/radexpand/issues).

> **Önemli:** RadExpand bir **tıbbi cihaz değildir**. Tanı veya tedavi kararı vermez; yalnızca yazmayı hızlandırır. Oluşan metni kontrol etmek kullanıcının sorumluluğundadır.

---

## English

### What it does
Type a short abbreviation, press a key, get the full text:

| You type | Result |
|---|---|
| `hyi` + **Space** | `hyperintense␣` |
| `hyi` + **Enter** | `hyperintense` + new line |
| `hyi` + **Tab** + `ly` | `hyperintensely`: Tab adds nothing, the cursor stays right after the text so you can add a suffix |
| `xhyi` + Space | `xhyi␣`: `hyi` inside a longer word does **not** expand |
| `Hyi` + Space | `Hyperintense␣`: capitalisation is kept |
| **Backspace** right after an expansion | your abbreviation comes back |

- **Fully offline.** The Android app has no internet permission; what you type cannot leave the device.
- Imports **Espanso** files (`base.yml`) with a preview, and exports to Espanso format.
- **Tablet ↔ Windows sync** through a shared file (e.g. a Google Drive folder). The latest change wins; deletions are synced too.
- **Templates:** `$|$` marks where the cursor stops. With several `$|$` marks, Tab jumps to the next one. Supports `{date}` / `{time}` variables.
- **Everything is configurable in Settings:**
  - what Space / Enter / Tab do, plus a custom expand key (e.g. F2);
  - word rules and capitalisation;
  - theme, colour and text size;
  - backups and per-app disable.
- Includes a short in-app **"How to use"** guide and an optional **sample radiology pack**.
- The interface is in Turkish and English; it asks which one at first launch.

### Download
[**Latest release →**](https://github.com/Oraddi/radexpand/releases/latest)

| File | For |
|---|---|
| `RadExpand-x.y.z.apk` | Android 8+ tablet/phone with a physical keyboard |
| `RadExpand-x.y.z.msi` | Windows 10/11 installer (no admin rights needed) |
| `RadExpand-Windows-x.y.z.zip` | Windows portable (no install) |

### Android setup
1. Open the APK on the tablet and tap **Install**. Allow installs from this source if asked.
2. Open RadExpand and choose a language. Then follow the two setup steps: **enable RadExpand in keyboard settings**, then **select RadExpand as the keyboard**.
   Android shows a "may collect what you type" warning for every keyboard; RadExpand has no internet permission.
3. When no physical keyboard is connected, a small bar appears on screen so you can switch to your usual keyboard.

**Automatic updates (optional):** the app itself never goes online. To get new versions automatically, add this repository (`https://github.com/Oraddi/radexpand`) to [Obtainium](https://obtainium.imranr.dev/). Updates install over the old version with the same signature, and your shortcuts are kept.

### Windows setup
1. Run the `.msi`. If SmartScreen says "unrecognised app", choose **More info → Run anyway** (the app has no paid code-signing certificate).
2. RadExpand lives in the system tray. Closing the window keeps it running; right-click the tray icon for Open / Pause / Exit.
3. **If you use Espanso, close it.** Running both expands shortcuts twice, and RadExpand warns you about it.
4. **Updates:** one small request goes to GitHub at startup and once a day; nothing you type is ever sent. When a new version exists, **Settings → Updates → Download and install** appears. It never installs without asking, and the check can be turned off.

### Limitations
- Works with a **physical keyboard** only; nothing is expanded while typing on the on-screen keyboard.
- On Windows, programs running **as administrator** (some RIS/PACS clients) require RadExpand to run as administrator too.
- Windows cannot generally detect password fields, so turn RadExpand off for your password manager in **Settings → Per-app**. On Android, expansion in password fields is off by default.
- **Testing status:** tested with automated tests on an Android 15 tablet emulator (including Chrome) and on Windows 11. There is little real-world experience yet on physical Samsung tablets or with Google Docs. Please [report problems](https://github.com/Oraddi/radexpand/issues).

> **Important:** RadExpand is **not a medical device**. It makes no diagnostic or treatment decisions; it only speeds up typing. Checking the resulting text is the user's responsibility.

---

## Geliştirme / Development

```
core/       engine, data model, Espanso import/export, sync merge (pure Kotlin, unit-tested)
ui-shared/  Compose screens shared by Android and Windows
app/        Android keyboard service (InputMethodService) + app
desktop/    Windows: low-level keyboard hook (JNA), SendInput, tray, Compose UI
tools/      emulator / Windows end-to-end test scripts (PowerShell)
```

Requirements: JDK 21 and the Android SDK (`ANDROID_HOME`).

```bash
./gradlew :core:test                    # unit tests
./gradlew :app:assembleRelease          # APK (unsigned unless a keystore is configured)
./gradlew :desktop:packageMsi           # Windows installer
```

Releases are built by GitHub Actions when a `v*` tag is pushed (see `.github/workflows/release.yml`). The APK signing key is stored only in repository secrets.

## Lisans / License
[MIT](LICENSE) © Umitcan Yildiz. Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
