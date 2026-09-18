<#
.SYNOPSIS
    Shared helpers for the release-* scripts.

.DESCRIPTION
    Dot-source from a script's own directory, after setting $ToolName so failures name the script the
    user actually ran:

        $ToolName = 'release-apk'
        . (Join-Path $PSScriptRoot '_common.ps1')

    Every helper reads paths relative to the repo root, so call them after Push-Location.

    build-apk.ps1 and build-aab.ps1 deliberately do not use this: they have no guard rails and no
    version handling, so there is nothing for them to share.
#>

# Keep this file ASCII-only: it has no BOM, so Windows PowerShell reads it as the ANSI
# codepage and any non-ASCII character reaches the console as mojibake.

# Prints a red message and stops the calling script. Dot-sourced, so `exit` ends that script and its
# `finally` block still runs.
function Fail([string]$message) {
    Write-Host "${ToolName}: $message" -ForegroundColor Red
    exit 1
}

function Get-StelaVersion {
    $match = [regex]::Match((Get-Content 'app/build.gradle.kts' -Raw -Encoding UTF8), 'stelaVersionName\s*=\s*"(.+?)"')
    if (-not $match.Success) { Fail 'could not find stelaVersionName in app/build.gradle.kts' }
    return $match.Groups[1].Value
}

# Mirrors the build script's derivation (major*10000 + minor*100 + patch). Returns 0 for a version it
# cannot parse, which callers treat as "no release-note file", never as versionCode zero.
function Get-StelaVersionCode([string]$version) {
    if ($version -match '^(\d+)\.(\d+)\.(\d+)$') {
        return [int]$Matches[1] * 10000 + [int]$Matches[2] * 100 + [int]$Matches[3]
    }
    return 0
}

# F-Droid names release notes by versionCode and publishes whatever it finds; Play's "What's new" field
# takes the same text. Returns the path whether or not the file exists, so callers can report a miss.
function Get-ReleaseNotePath([int]$versionCode) {
    if ($versionCode -le 0) { return $null }
    return "fastlane/metadata/android/en-US/changelogs/$versionCode.txt"
}

# Everything under this version's "## [x.y.z]" heading, up to the next one.
function Get-ChangelogSection([string]$version) {
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
    return ($body -join "`n").Trim()
}
