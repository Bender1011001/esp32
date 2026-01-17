$adb = "C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "com.chimera.red"
$act = "com.chimera.red.MainActivity"

Write-Host "Waiting for emulator to boot..."
& $adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'
Write-Host "Emulator Ready. Installing App..."
& $adb install -r $apk
Write-Host "Launching App..."
& $adb shell am start -n $pkg/$act
Write-Host "Done."
