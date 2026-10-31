$ErrorActionPreference = "Stop"

$proj = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = Join-Path $proj "build"

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }
    $found = Get-ChildItem "$env:LOCALAPPDATA\Programs" -Filter javac.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "jdk-17.*\\bin\\javac.exe$" } |
        Select-Object -First 1
    if ($found) { return Split-Path (Split-Path $found.FullName -Parent) -Parent }
    throw "JDK 17 not found. Set JAVA_HOME."
}

function Find-Sdk {
    foreach ($name in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value -and (Test-Path $value)) { return $value }
    }
    $local = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $local) { return $local }
    throw "Android SDK not found. Set ANDROID_SDK_ROOT."
}

$jdk = Find-JavaHome
$sdk = Find-Sdk
$jar = Join-Path $sdk "platforms\android-31\android.jar"
$bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") -or (Get-ChildItem $_.FullName -Recurse -Filter aapt2.exe -ErrorAction SilentlyContinue) } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $bt) { throw "build-tools with aapt2 not found." }
$aapt2 = Get-ChildItem $bt.FullName -Recurse -Filter aapt2.exe | Select-Object -First 1
$tools = Split-Path $aapt2.FullName -Parent
if (-not (Test-Path $jar)) { throw "Missing $jar" }

if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build | Out-Null
$env:JAVA_HOME = $jdk
$env:PATH = "$(Join-Path $jdk 'bin');$env:PATH"

& (Join-Path $tools "aapt2.exe") compile --dir (Join-Path $proj "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& (Join-Path $tools "aapt2.exe") link -o (Join-Path $build "base.apk") -I $jar --manifest (Join-Path $proj "AndroidManifest.xml") --java (Join-Path $build "gen") --min-sdk-version 26 --target-sdk-version 31 (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$sources = @(Get-ChildItem (Join-Path $build "gen"), (Join-Path $proj "src") -Recurse -Filter *.java | ForEach-Object FullName)
& (Join-Path $jdk "bin\javac.exe") --release 11 -encoding UTF-8 -classpath $jar -d (Join-Path $build "classes") @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classes = @(Get-ChildItem (Join-Path $build "classes") -Recurse -Filter *.class | ForEach-Object FullName)
& (Join-Path $tools "d8.bat") --min-api 26 --lib $jar --output $build @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Push-Location $build
& (Join-Path $jdk "bin\jar.exe") uf base.apk classes.dex
Pop-Location
& (Join-Path $tools "zipalign.exe") -p -f 4 (Join-Path $build "base.apk") (Join-Path $build "aligned.apk")

$ks = Join-Path $proj "debug.keystore"
if (-not (Test-Path $ks)) {
    & (Join-Path $jdk "bin\keytool.exe") -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey -dname "CN=Debug" -keyalg RSA -keysize 2048 -validity 10000
}
& (Join-Path $tools "apksigner.bat") sign --ks $ks --ks-pass pass:android --key-pass pass:android --out (Join-Path $build "gold.apk") (Join-Path $build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "sign failed" }
Write-Host "Built $(Join-Path $build 'gold.apk')"
