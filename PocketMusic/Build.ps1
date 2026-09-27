param([switch]$Install)

$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$sdk=Join-Path (Split-Path $root -Parent) '.tools'
$tools=Join-Path $sdk 'build-tools\30.0.3'
$android=Join-Path $sdk 'platforms\android-22\android.jar'
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$build=Join-Path $root 'build'
$dist=Join-Path $root 'dist'
$apk=Join-Path $dist 'Pocket-Music-NP601SH.apk'
$key=Join-Path (Split-Path $root -Parent) 'pocket-hearts.keystore'

if(!(Test-Path $android) -or !(Test-Path $tools)) {throw 'Android SDK packages are missing in ../.tools'}
if(!(Test-Path $key)) {throw 'Signing key missing. Build ../Pocket Hearts/Build.ps1 first'}
New-Item -ItemType Directory -Force $build,$dist,(Join-Path $build 'gen'),(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null

& (Join-Path $tools 'aapt.exe') package -f -m -J (Join-Path $build 'gen') -M (Join-Path $root 'AndroidManifest.xml') -S (Join-Path $root 'res') -I $android -F (Join-Path $build 'unsigned.apk')
if($LASTEXITCODE -ne 0) {throw 'aapt failed'}
& javac -source 8 -target 8 -encoding UTF-8 -classpath $android -d (Join-Path $build 'classes') (Join-Path $root 'src\dev\codex\pocketmusic\MainActivity.java')
if($LASTEXITCODE -ne 0) {throw 'javac failed'}
& java -cp (Join-Path $tools 'lib\dx.jar') com.android.dx.command.Main --dex "--output=$(Join-Path $build 'dex\classes.dex')" (Join-Path $build 'classes')
if($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $build 'dex\classes.dex'))) {throw 'dx failed'}
Push-Location (Join-Path $build 'dex')
try {& (Join-Path $tools 'aapt.exe') add (Join-Path $build 'unsigned.apk') 'classes.dex'}
finally {Pop-Location}
if($LASTEXITCODE -ne 0) {throw 'aapt add failed'}
& (Join-Path $tools 'zipalign.exe') -f 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'zipalign failed'}
& (Join-Path $tools 'apksigner.bat') sign --ks $key --ks-key-alias pockethearts --ks-pass pass:pockethearts --key-pass pass:pockethearts --out $apk (Join-Path $build 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'signing failed'}
& (Join-Path $tools 'apksigner.bat') verify --min-sdk-version 22 $apk
if($LASTEXITCODE -ne 0) {throw 'APK verification failed'}
Write-Host "Built $apk"
if($Install) {
    & $adb install -r $apk
    if($LASTEXITCODE -ne 0) {throw 'Install failed'}
    & $adb shell am start -n 'dev.codex.pocketmusic/.MainActivity'
    if($LASTEXITCODE -ne 0) {throw 'Launch failed'}
}
