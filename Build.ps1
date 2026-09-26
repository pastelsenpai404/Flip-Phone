param([switch]$Install)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = Join-Path $root '.tools'
$buildTools = Join-Path $sdk 'build-tools\30.0.3'
$platform = Join-Path $sdk 'platforms\android-22\android.jar'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist'
$keystore = Join-Path $root 'pocket-hearts.keystore'
$apk = Join-Path $dist 'Pocket-Hearts-NP601SH.apk'

if (!(Test-Path $platform) -or !(Test-Path $buildTools)) {
    throw 'Android SDK packages missing: platforms;android-22 and build-tools;30.0.3'
}
New-Item -ItemType Directory -Force $build, $dist, (Join-Path $build 'gen'), (Join-Path $build 'classes'), (Join-Path $build 'dex') | Out-Null

& (Join-Path $buildTools 'aapt.exe') package -f -m -J (Join-Path $build 'gen') -M (Join-Path $root 'AndroidManifest.xml') -S (Join-Path $root 'res') -I $platform -F (Join-Path $build 'unsigned.apk')
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
& javac -source 8 -target 8 -encoding UTF-8 -classpath $platform -d (Join-Path $build 'classes') (Join-Path $root 'src\dev\codex\pockethearts\MainActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classFiles = Get-ChildItem (Join-Path $build 'classes') -Recurse -Filter '*.class' | ForEach-Object FullName
& java -cp (Join-Path $buildTools 'lib\d8.jar') com.android.tools.r8.D8 --min-api 22 --lib $platform --output (Join-Path $build 'dex') $classFiles
if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $build 'dex\classes.dex'))) { throw 'D8 failed' }
Push-Location (Join-Path $build 'dex')
try { & (Join-Path $buildTools 'aapt.exe') add (Join-Path $build 'unsigned.apk') 'classes.dex' }
finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'aapt add failed' }
& (Join-Path $buildTools 'zipalign.exe') -f 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

if (!(Test-Path $keystore)) {
    & keytool -genkeypair -keystore $keystore -storepass pockethearts -keypass pockethearts -alias pockethearts -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Pocket Hearts, O=Local App, C=TH'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& (Join-Path $buildTools 'apksigner.bat') sign --ks $keystore --ks-key-alias pockethearts --ks-pass pass:pockethearts --key-pass pass:pockethearts --out $apk (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& (Join-Path $buildTools 'apksigner.bat') verify --min-sdk-version 22 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
Write-Host "Built $apk"

if ($Install) {
    if (!(Test-Path $adb)) { throw 'adb.exe missing from .tools\platform-tools' }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
    & $adb shell am start -n 'dev.codex.pockethearts/.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'Launch failed' }
}
