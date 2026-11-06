$RadTools = $(if ($env:RADEXPAND_TOOLS) { $env:RADEXPAND_TOOLS } else { Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'tools' })
# Test emulatorunu (Android 15 tablet, donanim klavyeli) olusturur ve penceresiz baslatir.
$sdk = (Join-Path $RadTools 'android-sdk')
$env:JAVA_HOME = (Get-ChildItem $RadTools -Directory | Where-Object Name -like 'jdk-21*' | Select-Object -First 1).FullName
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_AVD_HOME = (Join-Path $RadTools 'avd')
New-Item -ItemType Directory -Force $env:ANDROID_AVD_HOME | Out-Null
$name = 'radexpand_tablet'
if (-not (Test-Path "$env:ANDROID_AVD_HOME\$name.avd")) {
    'no' | & "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n $name -k 'system-images;android-35;google_apis;x86_64' -d pixel_tablet --force | Out-Null
    $cfg = "$env:ANDROID_AVD_HOME\$name.avd\config.ini"
    # Donanim klavyesi var: Android, Bluetooth/USB klavye bagliymis gibi davranir.
    (Get-Content $cfg) -replace '^hw.keyboard=.*', 'hw.keyboard=yes' | Set-Content $cfg -Encoding ascii
    if (-not (Select-String -Path $cfg -Pattern '^hw.keyboard=' -Quiet)) { Add-Content $cfg 'hw.keyboard=yes' -Encoding ascii }
    Add-Content $cfg 'hw.ramSize=4096' -Encoding ascii
}
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList "-avd $name -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect" -WindowStyle Hidden
& "$sdk\platform-tools\adb.exe" wait-for-device
do { Start-Sleep -Seconds 3; $b = (& "$sdk\platform-tools\adb.exe" shell getprop sys.boot_completed 2>$null) } until ($b -match '1')
'EMULATOR HAZIR'
& "$sdk\platform-tools\adb.exe" shell getprop ro.build.version.release
