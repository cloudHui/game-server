param(
    [ValidateSet('setup', 'install', 'setup-key', 'model', 'update-model', 'update-cli', 'doctor', 'help')]
    [string]$Command = 'setup'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$app = 'claude-deepseek'
$baseUrl = 'https://api.deepseek.com/anthropic'
$modelsUrl = 'https://api.deepseek.com/models'
$installUrl = 'https://claude.ai/install.ps1'
$configDir = Join-Path $env:LOCALAPPDATA 'claude-deepseek'
$configFile = Join-Path $configDir 'config.json'
$backupDir = Join-Path $configDir 'backups'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$skillInstaller = Join-Path $scriptDir 'install-matt-skills.ps1'

function Write-Log([string]$Message) { Write-Host "[$app] $Message" }
function Read-YesNo([string]$Prompt, [bool]$Default = $false) {
    $suffix = if ($Default) { '[Y/n]' } else { '[y/N]' }
    $answer = Read-Host "$Prompt $suffix"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
    return $answer -match '^(y|yes)$'
}
function Get-Models([string]$ApiKey) {
    try {
        $result = Invoke-RestMethod -Method Get -Uri $modelsUrl -Headers @{ Authorization = "Bearer $ApiKey" } -TimeoutSec 30
    } catch {
        throw "DeepSeek authentication failed: $($_.Exception.Message)"
    }
    $models = @($result.data | ForEach-Object { $_.id } | Where-Object { $_ })
    if ($models.Count -eq 0) { throw 'DeepSeek returned no models.' }
    return $models
}
function Select-Model([string[]]$Models, [string]$Preferred = '') {
    if ($env:DEEPSEEK_MODEL) {
        if ($Models -notcontains $env:DEEPSEEK_MODEL) { throw "Unavailable model: $($env:DEEPSEEK_MODEL)" }
        return $env:DEEPSEEK_MODEL
    }
    $defaultIndex = 0
    for ($i = 0; $i -lt $Models.Count; $i++) {
        if ($Models[$i] -eq 'deepseek-v4-pro' -or ($Preferred -and $Models[$i] -eq $Preferred)) { $defaultIndex = $i }
        Write-Host ('  {0}) {1}' -f ($i + 1), $Models[$i])
    }
    $answer = Read-Host "Select model number [default $($defaultIndex + 1)]"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $Models[$defaultIndex] }
    $index = 0
    if (-not [int]::TryParse($answer, [ref]$index) -or $index -lt 1 -or $index -gt $Models.Count) { throw 'Invalid model number.' }
    return $Models[$index - 1]
}
function Save-Config([string]$ApiKey, [string]$Model) {
    if ([string]::IsNullOrWhiteSpace($ApiKey) -or $ApiKey -match '\s') { throw 'API Key cannot be empty or contain whitespace.' }
    if ($Model -notmatch '^[A-Za-z0-9._:-]+$') { throw 'Invalid model name.' }
    New-Item -ItemType Directory -Force -Path $configDir, $backupDir | Out-Null
    if (Test-Path -LiteralPath $configFile) {
        $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
        Copy-Item -LiteralPath $configFile -Destination (Join-Path $backupDir "config.$stamp.json")
    }
    [ordered]@{ baseUrl = $baseUrl; apiKey = $ApiKey; model = $Model } |
        ConvertTo-Json | Set-Content -LiteralPath $configFile -Encoding UTF8
    $values = [ordered]@{
        ANTHROPIC_BASE_URL = $baseUrl
        ANTHROPIC_API_KEY = $ApiKey
        ANTHROPIC_MODEL = $Model
        CLAUDE_CODE_ATTRIBUTION_HEADER = '0'
        CLAUDE_CODE_DISABLE_EXPERIMENTAL_BETAS = '1'
        ENABLE_TOOL_SEARCH = 'false'
        DISABLE_AUTOUPDATER = '1'
    }
    [Environment]::SetEnvironmentVariable('ANTHROPIC_AUTH_TOKEN', $null, 'User')
    Remove-Item Env:ANTHROPIC_AUTH_TOKEN -ErrorAction SilentlyContinue
    foreach ($item in $values.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($item.Key, $item.Value, 'User')
        Set-Item -Path "Env:$($item.Key)" -Value $item.Value
    }
}
function Read-Config {
    if (-not (Test-Path -LiteralPath $configFile)) { throw "Config missing: $configFile. Run setup-key." }
    return Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json
}
function Install-Claude {
    $claude = Get-Command claude -ErrorAction SilentlyContinue
    if ($claude) {
        Write-Log "Claude Code ready: $($claude.Source)"
        return
    }
    Write-Log 'Installing Claude Code from Anthropic.'
    $installer = Invoke-RestMethod -UseBasicParsing -Uri $installUrl
    & ([scriptblock]::Create($installer))
    $localBin = Join-Path $env:USERPROFILE '.local\bin'
    if (($env:Path -split ';') -notcontains $localBin) { $env:Path += ";$localBin" }
    if (-not (Get-Command claude -ErrorAction SilentlyContinue)) { throw 'Claude installed but command is not available. Open a new terminal and retry.' }
}
function Install-Skills {
    if (-not (Test-Path -LiteralPath $skillInstaller)) { throw "Skill installer missing: $skillInstaller" }
    & $skillInstaller
}
function Configure-Key([bool]$Required) {
    $apiKey = $env:DEEPSEEK_API_KEY
    if (-not $apiKey) {
        $secure = Read-Host 'DeepSeek API Key (Enter to skip)' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if (-not $apiKey) {
        if ($Required) { throw 'API Key is required.' }
        Write-Log 'Key skipped. Run claude-deepseek setup-key later.'
        return
    }
    Write-Log 'Validating DeepSeek key.'
    $models = @(Get-Models $apiKey)
    $model = Select-Model $models
    Save-Config $apiKey $model
    Write-Log "Configured model: $model"
}
function Show-Help {
    Write-Host @'
Usage: claude-deepseek [command]

  setup          Install Claude, skills, and configure DeepSeek
  setup-key      Configure DeepSeek API Key
  model          List and replace model
  update-cli     Update Claude Code
  doctor         Check CLI, config, authentication, and models
  help           Show help
'@
}

switch ($Command) {
    { $_ -in @('setup', 'install') } { Install-Claude; Install-Skills; Configure-Key $false; break }
    'setup-key' { Configure-Key $true; break }
    { $_ -in @('model', 'update-model') } {
        $config = Read-Config
        $models = @(Get-Models $config.apiKey)
        Write-Log "Current model: $($config.model)"
        if (Read-YesNo 'Replace model?') {
            $model = Select-Model $models $config.model
            Save-Config $config.apiKey $model
            Write-Log "Model replaced: $model"
        }
        break
    }
    'update-cli' {
        if (-not (Get-Command claude -ErrorAction SilentlyContinue)) { throw 'Claude Code is not installed.' }
        & claude update
        break
    }
    'doctor' {
        $claude = Get-Command claude -ErrorAction SilentlyContinue
        Write-Log $(if ($claude) { "Claude Code: $($claude.Source)" } else { 'Claude Code: missing' })
        if (Test-Path -LiteralPath $configFile) {
            $config = Read-Config
            $models = @(Get-Models $config.apiKey)
            Write-Log "Config: $configFile"
            Write-Log "Model: $($config.model)"
            Write-Log "Authentication OK. Models: $($models -join ', ')"
        } else { Write-Log 'Config: missing' }
        break
    }
    'help' { Show-Help; break }
}
