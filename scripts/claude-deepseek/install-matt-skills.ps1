$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = 'mattpocock/skills'
$cavemanRef = '0a4b76776dfd9979bfe013d99b5562a03b743839'
$canonicalRoot = if ($env:AGENT_SKILLS_HOME) { $env:AGENT_SKILLS_HOME } else { Join-Path $env:USERPROFILE '.agents\skills' }
$codexRoot = if ($env:CODEX_HOME) { Join-Path $env:CODEX_HOME 'skills' } else { Join-Path $env:USERPROFILE '.codex\skills' }
$claudeRoot = if ($env:CLAUDE_HOME) { Join-Path $env:CLAUDE_HOME 'skills' } else { Join-Path $env:USERPROFILE '.claude\skills' }
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$instructionTemplate = Join-Path $scriptRoot 'matt-skills-required.md'
$stage = Join-Path ([IO.Path]::GetTempPath()) ("matt-skills-install-" + [guid]::NewGuid().ToString('N'))

function Expand-GitHubArchive([string]$Ref, [string]$Destination) {
    $archive = "$Destination.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/$repo/archive/$Ref.zip" -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $Destination
}
function Backup-Path([string]$Path, [string]$Label, [string]$Stamp) {
    if (Test-Path -LiteralPath $Path) {
        Move-Item -LiteralPath $Path -Destination (Join-Path "$canonicalRoot\.backups" "$Label.$Stamp")
    }
}
function Ensure-Rules([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType File -Force -Path $Path | Out-Null }
    $text = Get-Content -Raw -LiteralPath $Path
    if ($text -notmatch '<!-- matt-skills-required:start -->') {
        $template = Get-Content -Raw -LiteralPath $instructionTemplate
        Add-Content -LiteralPath $Path -Value "`r`n$template" -Encoding UTF8
    }
}

try {
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    $mainDir = Join-Path $stage 'main'
    $oldDir = Join-Path $stage 'old'
    Expand-GitHubArchive 'refs/heads/main' $mainDir
    Expand-GitHubArchive $cavemanRef $oldDir
    $mainRoot = Get-ChildItem -LiteralPath $mainDir -Directory | Select-Object -First 1
    $oldRoot = Get-ChildItem -LiteralPath $oldDir -Directory | Select-Object -First 1
    $sources = [ordered]@{
        'grill-with-docs' = Join-Path $mainRoot.FullName 'skills\engineering\grill-with-docs'
        'diagnose' = Join-Path $mainRoot.FullName 'skills\engineering\diagnosing-bugs'
        'tdd' = Join-Path $mainRoot.FullName 'skills\engineering\tdd'
        'caveman' = Join-Path $oldRoot.FullName 'skills\productivity\caveman'
    }
    New-Item -ItemType Directory -Force -Path $canonicalRoot, $codexRoot, $claudeRoot, "$canonicalRoot\.backups" | Out-Null
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
    foreach ($skill in $sources.Keys) {
        $target = Join-Path $canonicalRoot $skill
        Backup-Path $target $skill $stamp
        Copy-Item -Recurse -LiteralPath $sources[$skill] -Destination $target
        if ($skill -eq 'diagnose') {
            $skillFile = Join-Path $target 'SKILL.md'
            (Get-Content -Raw -LiteralPath $skillFile) -replace '(?m)^name: diagnosing-bugs$', 'name: diagnose' |
                Set-Content -LiteralPath $skillFile -Encoding UTF8
        }
        foreach ($client in @($codexRoot, $claudeRoot)) {
            $link = Join-Path $client $skill
            Backup-Path $link "$skill.$((Split-Path $client -Leaf))" $stamp
            New-Item -ItemType Junction -Path $link -Target $target | Out-Null
        }
    }
    Ensure-Rules (Join-Path $env:USERPROFILE 'AGENTS.md')
    Ensure-Rules (Join-Path $env:USERPROFILE 'CLAUDE.md')
    Write-Host 'Installed: grill-with-docs caveman diagnose tdd'
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -Recurse -Force -LiteralPath $stage }
    if (Test-Path -LiteralPath "$stage.zip") { Remove-Item -Force -LiteralPath "$stage.zip" }
}
