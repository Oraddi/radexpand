param([switch]$SkipInstall)
$RadTools = $(if ($env:RADEXPAND_TOOLS) { $env:RADEXPAND_TOOLS } else { Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'tools' })
# Emulator uzerinde Asama 1 kabul testlerini otomatik calistirir.
#  - Harfler ve bosluk: "adb emu event text" -> emulatorun DONANIM klavyesi (Turkce Q duzeni).
#    Bluetooth/USB klavyeyle ayni yol: tus once IME'ye (RadExpand), sonra uygulamaya gider.
#  - Enter/Tab/Backspace/Space tek tuslar: "adb shell input keyevent" (o da once IME'ye gider).
$ErrorActionPreference = 'Continue'
$adb = (Join-Path $RadTools 'android-sdk\platform-tools\adb.exe')
$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release.apk'
$tmp = Join-Path $env:TEMP 'radexpand-emu'
New-Item -ItemType Directory -Force $tmp | Out-Null

function Adb { & $adb @args 2>&1 }
function Dump {
    for ($try = 0; $try -lt 5; $try++) {
        $r = "$(Adb shell uiautomator dump /sdcard/ui.xml)"
        if ($r -match 'dumped') { break }
        Start-Sleep -Seconds 2
    }
    Adb pull /sdcard/ui.xml "$tmp\ui.xml" | Out-Null
    [xml][System.IO.File]::ReadAllText("$tmp\ui.xml", [System.Text.Encoding]::UTF8)
}
function Center($b) {
    $m = [regex]::Match($b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    @([int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2))
}
# Turkce Q fiziksel duzende hangi ABD tusuna basilirsa o karakter cikar
function ToTrQ([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $s.ToCharArray()) {
        $k = switch -CaseSensitive ([string]$ch) {
            'i' { "'" } 'ı' { 'i' } 'ş' { ';' } 'ç' { '.' } 'ö' { ',' } 'ü' { ']' } 'ğ' { '[' } '.' { '/' }
            default { [string]$ch }
        }
        [void]$sb.Append($k)
    }
    $sb.ToString()
}
function Txt($s) { Adb emu event text (ToTrQ $s) | Out-Null; Start-Sleep -Milliseconds 200 }
$special = @{ 'SPACE' = 'KEYCODE_SPACE'; 'ENTER' = 'KEYCODE_ENTER'; 'TAB' = 'KEYCODE_TAB'; 'BS' = 'KEYCODE_DEL'; 'SBS' = '' }
function Key($k) {
    # SBS = Shift+Backspace (varsayilan geri alma tusu)
    if ($k -eq 'SBS') { Adb shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_DEL | Out-Null } else { Adb shell input keyevent $special[$k] | Out-Null }
    Start-Sleep -Milliseconds 150
}

$tests = @(
    @{ how = 'ri  (başka tuşa basmayın)';         keys = @('ri');                        exp = 'ri' },
    @{ how = 'ri + Space';                        keys = @('ri', 'SPACE');               exp = 'hiperintensite ' },
    @{ how = 'ri + Enter';                        keys = @('ri', 'ENTER');               exp = "hiperintensite`n" },
    @{ how = 'ri + Tab';                          keys = @('ri', 'TAB');                 exp = 'hiperintensite' },
    @{ how = 'ri + Tab + ler';                    keys = @('ri', 'TAB', 'ler');          exp = 'hiperintensiteler' },
    @{ how = 'birinci + Space';                   keys = @('birinci', 'SPACE');          exp = 'birinci ' },
    @{ how = 't2r + Space';                       keys = @('t2r', 'SPACE');              exp = 'T2 hiperintens ' },
    @{ how = "drimza  (Space'e basmayın)";          keys = @('drimza');                      exp = 'Dr. Ayşe ÖZTÜRK' },
    @{ how = 'izş + Space';                       keys = @('izş', 'SPACE');              exp = 'izlenmiştir. ' },
    @{ how = 'ç + Space';                         keys = @('ç', 'SPACE');                exp = 'çapında ' },
    @{ how = 'aç + Space';                        keys = @('aç', 'SPACE');               exp = 'aç ' },
    @{ how = 'bel + Space';                       keys = @('bel', 'SPACE');              exp = 'bel ' },
    @{ how = 'bel + Tab';                         keys = @('bel', 'TAB');                exp = 'belirginleşme' },
    @{ how = 'ri + Space + Shift+Backspace';      keys = @('ri', 'SPACE', 'SBS');        exp = 'ri ' },
    @{ how = 'ri + Space + Backspace';            keys = @('ri', 'SPACE', 'BS');         exp = 'hiperintensite' },
    @{ how = 'rix + Space';                       keys = @('rix', 'SPACE');              exp = 'rix ' },
    @{ how = 'ri + Enter'; single = $true;        keys = @('ri', 'ENTER');               exp = 'hiperintensite' },
    @{ how = 'olabildiğince hızlı: ri Space ri Space ri Space ri Space ri Space'; keys = @('ri ri ri ri ri', 'SPACE'); exp = ('hiperintensite ' * 5) },
    @{ how = 'rtü + Space';                       keys = @('rtü', 'SPACE');              exp = "Raporda tanımlı bulgular`n`nDr. Ayşe ÖZTÜRK " },
    @{ how = 'Ri + Space';                        keys = @('Ri', 'SPACE');               exp = 'Hiperintensite ' },
    @{ how = 'kcc + Space + 152';                 keys = @('kcc', 'SPACE', '152');       exp = 'Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür.' }
)

if (-not $SkipInstall) { Adb install -r $apk | Select-Object -Last 1 }
# force-stop klavye servisini de oldurur; IME secimi ondan SONRA yapilmali.
Adb shell am force-stop com.radexpand.app | Out-Null
Adb shell ime enable com.radexpand.app/.RadExpandIme | Out-Null
Adb shell ime set com.radexpand.app/.RadExpandIme | Out-Null
# Yeni acilan emulatorde sistem kendi klavyesini geri secebilir: RadExpand secili olana kadar tekrarla.
for ($i = 0; $i -lt 10; $i++) {
    if ((Adb shell settings get secure default_input_method) -match 'radexpand') { break }
    Start-Sleep -Seconds 2
    Adb shell ime set com.radexpand.app/.RadExpandIme | Out-Null
}
Start-Sleep -Seconds 1
Adb shell am start -n com.radexpand.app/.TestActivity | Out-Null
Start-Sleep -Seconds 3

function FindField($label, [bool]$wantEmpty) {
    1..6 | ForEach-Object { Adb shell input swipe 600 400 600 1500 80 | Out-Null }
    Start-Sleep -Milliseconds 300
    for ($scroll = 0; $scroll -lt 30; $scroll++) {
        $x = Dump
        $nodes = @($x.SelectNodes('//node'))
        for ($i = 0; $i -lt $nodes.Count; $i++) {
            if ($nodes[$i].text -ceq $label -or $nodes[$i].text -ceq "Yazın: $label" -or $nodes[$i].text -ceq "Type: $label") {
                for ($j = $i + 1; $j -lt $nodes.Count; $j++) {
                    if ($nodes[$j].class -eq 'android.widget.EditText') {
                        $b = $nodes[$j].bounds
                        $c = Center $b
                        # Ekranin alt kenarina cok yakinsa biraz daha kaydir
                        if ($c[1] -lt 1450 -and -not ($wantEmpty -and $nodes[$j].text)) { return $nodes[$j] }
                        break
                    }
                }
            }
        }
        Adb shell input swipe 600 1300 600 800 300 | Out-Null
        Start-Sleep -Milliseconds 400
    }
    return $null
}
function FocusedText {
    $x = Dump
    $n = $x.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.focused -eq 'true' } | Select-Object -First 1
    if ($n) { $n.text } else { '?' }
}
function Vis($s) { $s -replace ' ', '·' -replace "`n", '⏎' -replace "`t", '⇥' }

$results = @()
foreach ($t in $tests) {
    $f = FindField $t.how ([bool]$t.single)
    if (-not $f) { $results += [pscustomobject]@{ Test = $t.how; Sonuc = 'KUTU YOK'; Cikti = '' }; continue }
    $c = Center $f.bounds
    Adb shell input tap $c[0] $c[1] | Out-Null
    Start-Sleep -Milliseconds 400
    foreach ($k in $t.keys) { if ($special.ContainsKey($k)) { Key $k } else { Txt $k } }
    Start-Sleep -Milliseconds 600
    # Tek satirli alanda Enter odagi sonraki kutuya tasir: ayni kutuyu konumundan oku
    $x = Dump
    $after = $x.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.bounds -eq $f.bounds } | Select-Object -First 1
    $out = if ($after) { $after.text } else { FocusedText }
    $ok = ($out -ceq $t.exp)
    $results += [pscustomobject]@{ Test = $t.how; Sonuc = $(if ($ok) { 'GECTI' } else { 'KALDI' }); Cikti = (Vis $out) }
}

# --- Chromium (WebView) alanlari: ayri surecte calisan test sayfasi
# (Sayfa disa kapali; uygulamadaki dugmeyle acilir)
$btn = $null
for ($s = 0; $s -lt 30 -and -not $btn; $s++) {
    $x = Dump
    $btn = $x.SelectNodes('//node') | Where-Object { $_.text -ceq 'Chrome motoru test sayfasını aç' -or $_.text -ceq 'Open the Chrome engine test page' } | Select-Object -First 1
    if (-not $btn) { Adb shell input swipe 600 1300 600 800 300 | Out-Null; Start-Sleep -Milliseconds 400 }
}
if ($btn) { $c = Center $btn.bounds; Adb shell input tap $c[0] $c[1] | Out-Null }
Start-Sleep -Seconds 4
$webSeq = @('ri', 'SPACE', 'ri', 'TAB', 'ler', 'SPACE', 'ri', 'ENTER', 'kcc', 'SPACE', '152')
$webExp = 'hiperintensite·hiperintensiteler·hiperintensite⏎Karaciğer·kraniokaudal·boyutu·152·mm·ölçülmüştür.'
foreach ($lbl in @('textarea', 'contenteditable')) {
    $f = FindField $lbl $false
    if (-not $f) { $results += [pscustomobject]@{ Test = "WebView $lbl"; Sonuc = 'KUTU YOK'; Cikti = '' }; continue }
    $c = Center $f.bounds
    Adb shell input tap $c[0] $c[1] | Out-Null
    Start-Sleep -Milliseconds 600
    foreach ($k in $webSeq) { if ($special.ContainsKey($k)) { Key $k } else { Txt $k } }
    Start-Sleep -Milliseconds 800
    $x = Dump
    $rep = ($x.SelectNodes('//node') | Where-Object { $_.text -like 'textarea: «*' } | Select-Object -First 1).text
    $line = ($rep -split "`n") | Where-Object { $_ -like "${lbl}:*" } | Select-Object -First 1
    $val = [regex]::Match("$line", '«(.*)»').Groups[1].Value
    $ok = ($val -replace '⏎⏎', '⏎').TrimEnd('⏎').StartsWith($webExp)
    $results += [pscustomobject]@{ Test = "WebView $lbl : ri␣ ri⇥ler␣ ri⏎ kcc␣152"; Sonuc = $(if ($ok) { 'GECTI' } else { 'KALDI' }); Cikti = $val }
}

$results | Format-Table -AutoSize | Out-String -Width 220
"Gecen: $(@($results | Where-Object Sonuc -eq 'GECTI').Count) / $($results.Count)"
