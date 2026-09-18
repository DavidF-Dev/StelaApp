<#
.SYNOPSIS
    Build a signed Android App Bundle for upload to the Play Console.

.DESCRIPTION
    Reads the version from app/build.gradle.kts (single source of truth), checks the same guard
    rails as release-apk.ps1 minus the GitHub ones, builds the signed .aab, stages a
    version-stamped copy, and prints the CHANGELOG section ready to paste into the track's
    "What's new" field.

    It uploads nothing. Play submissions stay a deliberate manual step in the Console, so this
    script stops at "here is the file". It also creates no tag and touches no remote, which is
    why it can be run for the same version as release-apk.ps1, in either order.

    Prerequisite: a real keystore.properties, or the bundle is debug-signed and Play rejects it.

    Before running: bump stelaVersionName in app/build.gradle.kts and write the matching
    CHANGELOG.md "## [x.y.z]" section, then commit. This script reads both; it changes neither.
    Every Play upload needs its own versionCode, which is derived from stelaVersionName - so a
    second upload means a version bump, not a second run at the same version.

.EXAMPLE
    powershell -File scripts/release-aab.ps1
#>
[CmdletBinding()]
param()

# Default ErrorActionPreference is deliberately left as Continue: setting it to Stop turns
# native-command stderr (git writes status there even on success) into terminating errors on
# Windows PowerShell. Guard rails check exit codes explicitly instead.

# Keep this file ASCII-only: it has no BOM, so Windows PowerShell reads it as the ANSI
# codepage and any non-ASCII character reaches the console as mojibake.

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    function Fail([string]$message) {
        Write-Host "release-aab: $message" -ForegroundColor Red
        exit 1
    }

    # --- Version: single source of truth is stelaVersionName in the build script ---
    $match = [regex]::Match((Get-Content 'app/build.gradle.kts' -Raw -Encoding UTF8), 'stelaVersionName\s*=\s*"(.+?)"')
    if (-not $match.Success) { Fail 'could not find stelaVersionName in app/build.gradle.kts' }
    $version = $match.Groups[1].Value

    # --- Guard rails: fail before building anything ---
    if (git status --porcelain) { Fail 'working tree is dirty - commit or stash changes first' }

    if (-not (Test-Path 'keystore.properties')) {
        Fail 'keystore.properties is missing - the bundle would be debug-signed and Play would reject it'
    }

    # --- Extract this version's CHANGELOG section (everything under "## [x.y.z]") ---
    # Doubles as the track's "What's new" text, so it is required rather than optional here.
    $notes = & {
        $body = @()
        $inSection = $false
        foreach ($line in (Get-Content 'CHANGELOG.md' -Encoding UTF8)) {
            if ($line -match '^##\s+\[(.+?)\]') {
                if ($inSection) { break }                                      # next version, stop
                if ($Matches[1] -eq $version) { $inSection = $true; continue } # our heading, start
            } elseif ($inSection) {
                $body += $line
            }
        }
        ($body -join "`n").Trim()
    }
    if ([string]::IsNullOrWhiteSpace($notes)) { Fail "no CHANGELOG.md section found for [$version]" }

    # --- Build the signed release bundle ---
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    Write-Host "Building signed release bundle for $version ..." -ForegroundColor Cyan
    & .\gradlew.bat bundleRelease
    if ($LASTEXITCODE -ne 0) { Fail 'bundleRelease failed' }
    $aab = 'app/build/outputs/bundle/release/stela-release.aab'
    if (-not (Test-Path $aab)) { Fail "expected AAB not found at $aab" }
    # Version-stamped copy, so several builds sitting in the output folder stay tellable apart
    # when picking one to upload.
    $bundle = "app/build/outputs/bundle/release/stela-$version.aab"
    Copy-Item $aab $bundle -Force
    $bundleSizeMb = [math]::Round((Get-Item $bundle).Length / 1MB, 2)

    Write-Host ''
    Write-Host 'Bundle ready for the Play Console. Nothing was uploaded.' -ForegroundColor Green
    Write-Host "  Version : $version"
    Write-Host "  AAB     : $bundle  ($bundleSizeMb MB)"
    Write-Host "  What's new (from CHANGELOG.md):"
    $notes -split "`n" | ForEach-Object { Write-Host "      $_" }
}
finally {
    Pop-Location
}
