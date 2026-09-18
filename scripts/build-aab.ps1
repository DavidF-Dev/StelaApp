<#
.SYNOPSIS
    Build the release Android App Bundle. No guard rails.

.DESCRIPTION
    Builds and reports where the artifact landed, nothing more: no version, changelog or
    working-tree checks, and nothing is uploaded anywhere. For a quick local artifact - use
    release-aab.ps1 for a bundle you intend to put on a Play track.

    Without a keystore.properties the build falls back to debug signing, which the Play Console
    rejects; the summary says which key you got rather than refusing to build.

.EXAMPLE
    powershell -File scripts/build-aab.ps1
#>
[CmdletBinding()]
param()

# Keep this file ASCII-only: it has no BOM, so Windows PowerShell reads it as the ANSI
# codepage and any non-ASCII character reaches the console as mojibake.

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    & .\gradlew.bat bundleRelease
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'build-aab: bundleRelease failed' -ForegroundColor Red
        exit 1
    }

    $aab = 'app/build/outputs/bundle/release/stela-release.aab'
    if (-not (Test-Path $aab)) {
        Write-Host "build-aab: expected AAB not found at $aab" -ForegroundColor Red
        exit 1
    }

    $signedWith = if (Test-Path 'keystore.properties') { 'release key' } else { 'DEBUG key - Play will reject it' }
    Write-Host ''
    Write-Host 'AAB built.' -ForegroundColor Green
    Write-Host "  Path   : $aab"
    Write-Host "  Size   : $([math]::Round((Get-Item $aab).Length / 1MB, 2)) MB"
    Write-Host "  Signed : $signedWith"
}
finally {
    Pop-Location
}
