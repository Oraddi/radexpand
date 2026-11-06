$RadTools = $(if ($env:RADEXPAND_TOOLS) { $env:RADEXPAND_TOOLS } else { Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'tools' })
# Emulator arayuz otomasyonu icin yardimci fonksiyonlar (dot-source ile kullanilir: . .\tools\emu-ui.ps1)
$script:adb = (Join-Path $RadTools 'android-sdk\platform-tools\adb.exe')
$script:uiTmp = Join-Path $env:TEMP 'radexpand-emu'
New-Item -ItemType Directory -Force $script:uiTmp | Out-Null

function Adb { & $script:adb @args 2>&1 }
function UiDump {
    for ($t = 0; $t -lt 5; $t++) {
        if ("$(Adb shell uiautomator dump /sdcard/ui.xml)" -match 'dumped') { break }
        Start-Sleep -Seconds 1
    }
    Adb pull /sdcard/ui.xml "$script:uiTmp\ui.xml" | Out-Null
    [xml][System.IO.File]::ReadAllText("$script:uiTmp\ui.xml", [System.Text.Encoding]::UTF8)
}
function UiCenter($b) {
    $m = [regex]::Match($b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    @([int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2))
}
function UiFind([string]$text, [switch]$Contains) {
    $x = UiDump
    foreach ($n in $x.SelectNodes('//node')) {
        $t = "$($n.text)"; $d = "$($n.'content-desc')"
        if ($Contains) { if ($t.Contains($text) -or $d.Contains($text)) { return $n } }
        elseif ($t -ceq $text -or $d -ceq $text) { return $n }
    }
    $null
}
function UiTap([string]$text, [switch]$Contains, [int]$WaitSec = 10) {
    $end = (Get-Date).AddSeconds($WaitSec)
    do {
        $n = if ($Contains) { UiFind $text -Contains } else { UiFind $text }
        if ($n) { $c = UiCenter $n.bounds; Adb shell input tap $c[0] $c[1] | Out-Null; Start-Sleep -Milliseconds 600; return $true }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $end)
    Write-Output "BULUNAMADI: $text"
    $false
}
function UiShot([string]$name) {
    $p = Join-Path $env:RADEXPAND_SHOTS "$name.png"
    cmd /c "`"$script:adb`" exec-out screencap -p > `"$p`""
    $p
}
# Turkce Q fiziksel duzende yazim (donanim klavyesi yolu)
function TrQ([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $s.ToCharArray()) {
        $k = switch -CaseSensitive ([string]$ch) {
            'i' { "'" } 'ı' { 'i' } 'ş' { ';' } 'ç' { '.' } 'ö' { ',' } 'ü' { ']' } 'ğ' { '[' } '.' { '/' } 'İ' { '"' }
            default { [string]$ch }
        }
        [void]$sb.Append($k)
    }
    $sb.ToString()
}
function TypeTr([string]$s) { Adb emu event text (TrQ $s) | Out-Null; Start-Sleep -Milliseconds 250 }
function KeyEv([string]$k) { Adb shell input keyevent $k | Out-Null; Start-Sleep -Milliseconds 200 }
