$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$rawBase = if ($env:CLAUDE_DEEPSEEK_RAW_BASE) { $env:CLAUDE_DEEPSEEK_RAW_BASE } else { 'https://raw.githubusercontent.com/cloudHui/game-server/main/scripts/claude-deepseek' }
$installDir = if ($env:CLAUDE_DEEPSEEK_INSTALL_DIR) { $env:CLAUDE_DEEPSEEK_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA 'claude-deepseek' }
$binDir = Join-Path $env:USERPROFILE '.local\bin'

New-Item -ItemType Directory -Force -Path $installDir, $binDir | Out-Null
foreach ($file in @('claude-deepseek.ps1', 'install-matt-skills.ps1', 'matt-skills-required.md')) {
    $localSource = if ($PSScriptRoot) { Join-Path $PSScriptRoot $file } else { $null }
    if ($localSource -and (Test-Path -LiteralPath $localSource)) {
        Copy-Item -Force -LiteralPath $localSource -Destination (Join-Path $installDir $file)
    } else {
        Invoke-WebRequest -UseBasicParsing -Uri "$rawBase/$file" -OutFile (Join-Path $installDir $file)
    }
}

$launcher = @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$installDir\claude-deepseek.ps1" %*
"@
Set-Content -LiteralPath (Join-Path $binDir 'claude-deepseek.cmd') -Value $launcher -Encoding ASCII

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$pathParts = @($userPath -split ';' | Where-Object { $_ })
if ($pathParts -notcontains $binDir) {
    $newPath = (@($pathParts) + $binDir) -join ';'
    [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
}
if (($env:Path -split ';') -notcontains $binDir) { $env:Path += ";$binDir" }

& (Join-Path $installDir 'claude-deepseek.ps1') setup
