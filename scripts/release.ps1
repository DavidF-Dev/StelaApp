<#
.SYNOPSIS
    Build, sign, and publish a Stela release to GitHub Releases.

.DESCRIPTION
    Reads the version from app/build.gradle.kts (single source of truth), checks guard
    rails, builds the signed release APK, then — after explicit confirmation — creates the
    GitHub Release with the APK attached and the matching CHANGELOG.md section as the body.
    The signing key stays local; nothing is published until you confirm.

    Prerequisites: gh CLI installed and authenticated (gh auth login), and a real
    keystore.properties present so the build is release-signed (not debug-signed).

    Before running: bump stelaVersionName in app/build.gradle.kts and write the matching
    CHANGELOG.md "## [x.y.z]" section, then commit.

.PARAMETER Force
    Skip the confirmation prompt (for non-interactive use). Off by default.

.EXAMPLE
    powershell -File scripts/release.ps1
#>
[CmdletBinding()]
param([switch]$Force)

# Default ErrorActionPreference is deliberately left as Continue: setting it to Stop turns
# native-command stderr (git/gh write status there even on success) into terminating
# errors on Windows PowerShell. Guard rails check exit codes explicitly instead.

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    function Fail([string]$message) {
        Write-Host "release: $message" -ForegroundColor Red
        exit 1
    }

    # The signing certificate's SHA-256 — constant for the app's lifetime (it changes only
    # if the signing key changes). Published so users can verify the downloaded APK.
    $fingerprint = '34:24:47:8D:3E:3A:8A:FC:1A:6E:37:6A:ED:C3:8A:E7:59:B6:F0:FB:09:4A:B3:79:BA:07:AD:43:64:2F:D3:18'

    # --- Version: single source of truth is stelaVersionName in the build script ---
    $match = [regex]::Match((Get-Content 'app/build.gradle.kts' -Raw -Encoding UTF8), 'stelaVersionName\s*=\s*"(.+?)"')
    if (-not $match.Success) { Fail 'could not find stelaVersionName in app/build.gradle.kts' }
    $version = $match.Groups[1].Value
    $tag = "v$version"

    # --- Guard rails: fail before building anything ---
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Fail 'gh CLI not found — install it, then run: gh auth login'
    }
    gh auth status 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'gh is not authenticated — run: gh auth login' }

    if (git status --porcelain) { Fail 'working tree is dirty — commit or stash changes first' }

    if (-not (Test-Path 'keystore.properties')) {
        Fail 'keystore.properties is missing — the build would be debug-signed'
    }

    if (git tag --list $tag) { Fail "tag $tag already exists" }
    gh release view $tag 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Fail "release $tag already exists" }

    # --- Extract this version's CHANGELOG section (everything under "## [x.y.z]") ---
    $notes = & {
        $body = @()
        $inSection = $false
        foreach ($line in (Get-Content 'CHANGELOG.md' -Encoding UTF8)) {
            if ($line -match '^##\s+\[(.+?)\]') {
                if ($inSection) { break }                                    # next version → stop
                if ($Matches[1] -eq $version) { $inSection = $true; continue } # our heading → start
            } elseif ($inSection) {
                $body += $line
            }
        }
        ($body -join "`n").Trim()
    }
    if ([string]::IsNullOrWhiteSpace($notes)) { Fail "no CHANGELOG.md section found for [$version]" }
    $releaseBody = "$notes`n`n---`nAPK SHA-256: ``$fingerprint``"

    # --- Build the signed release APK ---
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    Write-Host "Building signed release APK for $tag ..." -ForegroundColor Cyan
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { Fail 'assembleRelease failed' }
    $apk = 'app/build/outputs/apk/release/stela-release.apk'
    if (-not (Test-Path $apk)) { Fail "expected APK not found at $apk" }

    # --- Confirm before the outward, irreversible step (tag + publish) ---
    Write-Host ''
    Write-Host 'About to publish a GitHub Release:' -ForegroundColor Yellow
    Write-Host "  Tag / title : $tag  (gh creates the tag at HEAD)"
    Write-Host "  APK         : stela-$version.apk  (from $apk)"
    Write-Host '  Notes       :'
    $releaseBody -split "`n" | ForEach-Object { Write-Host "      $_" }
    Write-Host ''
    if (-not $Force) {
        if ((Read-Host "Type 'yes' to create and publish this release") -ne 'yes') {
            Write-Host 'Aborted — nothing was published.' -ForegroundColor Yellow
            exit 0
        }
    }

    # --- Publish ---
    $notesFile = New-TemporaryFile
    # UTF-8 without BOM, so the release body has no stray leading character on any PS version.
    [System.IO.File]::WriteAllText($notesFile.FullName, $releaseBody, (New-Object System.Text.UTF8Encoding($false)))
    # A release asset's download name is its file basename, so stage a version-stamped copy
    # (e.g. stela-1.0.0.apk) and upload that rather than the variant-named build output.
    $asset = Join-Path ([System.IO.Path]::GetTempPath()) "stela-$version.apk"
    Copy-Item $apk $asset -Force
    gh release create $tag $asset --title "Stela $tag" --notes-file $notesFile.FullName
    $published = ($LASTEXITCODE -eq 0)
    Remove-Item $notesFile -ErrorAction SilentlyContinue
    Remove-Item $asset -ErrorAction SilentlyContinue
    if (-not $published) { Fail 'gh release create failed' }
    Write-Host "Published $tag (stela-$version.apk)" -ForegroundColor Green
}
finally {
    Pop-Location
}
