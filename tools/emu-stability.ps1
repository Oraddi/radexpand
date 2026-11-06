param([string]$Shots = $env:TEMP)
# Asama 5 kararlilik denemeleri (emulator, gercek Chrome). Once tools\emu-start.ps1 calismis olmali
# ve test sayfasi http://localhost:8765/test.html (adb reverse) ile sunuluyor olmali.
$env:RADEXPAND_SHOTS = $Shots
. (Join-Path $PSScriptRoot 'emu-ui.ps1')

function FocusTextarea {
    for ($t = 0; $t -lt 5; $t++) {
        $x = UiDump
        $ed = @($x.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.package -eq 'com.android.chrome' })
        # Ilk EditText adres cubugu olabilir; sayfa icindeki ilk kutu (textarea) ust kisimdan sonra gelir
        $ta = $ed | Where-Object { (UiCenter $_.bounds)[1] -gt 250 } | Select-Object -First 1
        if ($ta) {
            $m = [regex]::Match($ta.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
            # Kutunun alt kismina dokun: imlec metnin sonuna gider
            Adb shell input tap ([int]$m.Groups[1].Value + 40) ([int]$m.Groups[4].Value - 15) | Out-Null
            Start-Sleep -Milliseconds 800
            $f = (UiDump).SelectNodes('//node') | Where-Object { $_.focused -eq 'true' -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
            if ($f -and (UiCenter $f.bounds)[1] -gt 250) { return $true }
        }
        KeyEv KEYCODE_ESCAPE
        Start-Sleep -Seconds 1
    }
    $false
}
function Result { [regex]::Match("$((UiFind 'SONUC' -Contains).text)", 'textarea=«(.*?)»').Groups[1].Value }

Adb shell ime set com.radexpand.app/.RadExpandIme | Out-Null
Adb shell am start -a android.intent.action.VIEW -d "http://localhost:8765/test.html?k=$(Get-Random)" com.android.chrome | Out-Null
Start-Sleep -Seconds 5
UiTap 'No thanks' -WaitSec 2 | Out-Null
$out = [ordered]@{}

# 1) Uzun metin, tek komutta hizli (bosluklar donanim klavyesinden; kisayollar gercek base.yml'den)
if (FocusTextarea) {
    TypeTr 'Beyin parankiminde p1 sekanslarda t2r ve t2f ri odaklar izş bilateral fr ve temp lobda bc de milimetrik ri alanlar kr iskemik değişikliklerle uy '
    Start-Sleep -Seconds 2
    $out['uzun'] = Result
} else { $out['uzun'] = 'ODAK YOK' }

# 2) Ekran kilidi ve acilis
KeyEv KEYCODE_SLEEP; Start-Sleep -Seconds 2; KeyEv KEYCODE_WAKEUP; Adb shell wm dismiss-keyguard | Out-Null; Start-Sleep -Seconds 2
if (FocusTextarea) { TypeTr 'kilit ri'; KeyEv KEYCODE_SPACE; Start-Sleep -Seconds 1; $out['kilit'] = Result } else { $out['kilit'] = 'ODAK YOK' }

# 3) Uygulama degistirme: RadExpand'i ac, Geri ile Chrome'a don
Adb shell am start -n com.radexpand.app/.MainActivity | Out-Null; Start-Sleep -Seconds 2
KeyEv KEYCODE_BACK; Start-Sleep -Seconds 2
if (FocusTextarea) { TypeTr 'geçiş t2r'; KeyEv KEYCODE_TAB; TypeTr 'ler'; KeyEv KEYCODE_SPACE; Start-Sleep -Seconds 1; $out['gecis'] = Result } else { $out['gecis'] = 'ODAK YOK' }

$out.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }
