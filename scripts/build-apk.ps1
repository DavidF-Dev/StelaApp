<#
.SYNOPSIS
    Build the release APK. No guard rails.

.DESCRIPTION
    Builds and reports where the artifact landed, nothing more: no version, changelog or
    working-tree checks, and nothing is published anywhere. For a quick local artifact - use
    release-apk.ps1 for anything that goes out to people.

    Without a keystore.properties the build falls back to debug signing, which is fine locally and
    useless for distribution; the summary says which key you got rather than refusing to build.

.EXAMPLE
    powershell -File scripts/build-apk.ps1
#>
[CmdletBinding()]
param()

# Keep this file ASCII-only: it has no BOM, so Windows PowerShell reads it as the ANSI
# codepage and any non-ASCII character reaches the console as mojibake.

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'build-apk: assembleRelease failed' -ForegroundColor Red
        exit 1
    }

    $apk = 'app/build/outputs/apk/release/stela-release.apk'
    if (-not (Test-Path $apk)) {
        Write-Host "build-apk: expected APK not found at $apk" -ForegroundColor Red
        exit 1
    }

    $signedWith = if (Test-Path 'keystore.properties') { 'release key' } else { 'DEBUG key - not distributable' }
    Write-Host ''
    Write-Host 'APK built.' -ForegroundColor Green
    Write-Host "  Path   : $apk"
    Write-Host "  Size   : $([math]::Round((Get-Item $apk).Length / 1MB, 2)) MB"
    Write-Host "  Signed : $signedWith"
}
finally {
    Pop-Location
}
