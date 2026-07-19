[CmdletBinding()]
param(
    [string]$SdkRoot = 'D:\soft\Android\Sdk',
    [string]$GradleHome = 'D:\soft\Gradle\home'
)

$ErrorActionPreference = 'Stop'
$commandLineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'knowledge-reader-android-setup'
$archive = Join-Path $tempRoot 'commandlinetools.zip'
$unpack = Join-Path $tempRoot 'unpacked'
$latestRoot = Join-Path $SdkRoot 'cmdline-tools\latest'

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    throw '需要 winget。请先从 Microsoft Store 安装 App Installer。'
}

winget install --id EclipseAdoptium.Temurin.17.JDK --exact --source winget `
    --accept-package-agreements --accept-source-agreements --silent
winget install --id Google.AndroidStudio --exact --source winget `
    --accept-package-agreements --accept-source-agreements --silent

if (-not (Test-Path (Join-Path $latestRoot 'bin\sdkmanager.bat'))) {
    New-Item -ItemType Directory -Force -Path $tempRoot, $unpack, $latestRoot | Out-Null
    Invoke-WebRequest -Uri $commandLineToolsUrl -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $unpack -Force
    Copy-Item -Path (Join-Path $unpack 'cmdline-tools\*') -Destination $latestRoot -Recurse -Force
}

$sdkManager = Join-Path $latestRoot 'bin\sdkmanager.bat'
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory |
    Where-Object Name -Like 'jdk-17*' |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $jdk) { throw '没有找到 JDK 17。' }

$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

1..20 | ForEach-Object { 'y' } | & $sdkManager --licenses | Out-Host
& $sdkManager 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' 'emulator'

[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkRoot, 'User')
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME', $GradleHome, 'User')

$localProperties = "sdk.dir=$($SdkRoot.Replace('\', '\\'))"
Set-Content -LiteralPath (Join-Path $PSScriptRoot '..\local.properties') -Value $localProperties -Encoding UTF8

Write-Host "Android SDK ready: $SdkRoot"
Write-Host 'Open a new terminal, then run: .\gradlew.bat assembleDebug'
