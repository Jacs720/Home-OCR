param(
    [ValidatePattern('^[A-Z]$')]
    [string]$DriveLetter = 'P'
)

$ErrorActionPreference = 'Stop'
$androidRoot = $PSScriptRoot
$repositoryRoot = Split-Path -Parent $androidRoot
$previousGradleUserHome = $env:GRADLE_USER_HOME
$gradleUserHome = if ($env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME
} elseif ($env:USERPROFILE) {
    Join-Path $env:USERPROFILE '.gradle'
} else {
    Join-Path $repositoryRoot '.gradle-user-home'
}
$localSdk = Join-Path $repositoryRoot '.android-sdk'
$sdkRoot = if (Test-Path -LiteralPath $localSdk) {
    $localSdk
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    throw 'No se encontró Android SDK. Define ANDROID_SDK_ROOT o instala .android-sdk en el repositorio.'
}

$drive = "${DriveLetter}:"
if (Get-PSDrive -Name $DriveLetter -ErrorAction SilentlyContinue) {
    throw "La unidad $drive ya está en uso. Elige otra con -DriveLetter."
}

try {
    $env:GRADLE_USER_HOME = $gradleUserHome
    & subst $drive $repositoryRoot
    $mappedAndroid = "$drive\android"
    $mappedSdk = if ($sdkRoot.StartsWith($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)) {
        $drive + $sdkRoot.Substring($repositoryRoot.Length)
    } else {
        $sdkRoot
    }
    $propertyPath = ($mappedSdk -replace '\\', '/') -replace ':', '\:'
    Set-Content -LiteralPath "$androidRoot\local.properties" -Value "sdk.dir=$propertyPath" -Encoding ASCII

    & "$mappedAndroid\gradlew.bat" -p $mappedAndroid clean testDebugUnitTest lintDebug assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle terminó con código $LASTEXITCODE."
    }

    $distribution = Join-Path $repositoryRoot 'dist'
    New-Item -ItemType Directory -Force -Path $distribution | Out-Null
    Copy-Item -LiteralPath "$androidRoot\app\build\outputs\apk\debug\app-debug.apk" `
        -Destination "$distribution\PokemonHomeOCR-debug.apk" -Force
    Write-Host "APK generado: $distribution\PokemonHomeOCR-debug.apk"
}
finally {
    & subst $drive /D 2>$null
    $env:GRADLE_USER_HOME = $previousGradleUserHome
}
