<#
.SYNOPSIS
  Agentic Architect — Green Light Desktop Handoff & Auto-Boot (PowerShell)
.DESCRIPTION
  Retrieves bundle.zip from an iQOO smartphone via iQOO Office Kit or local HTTP,
  unpacks into ./scaffolded-workspace, runs npm install, and boots Cursor or VS Code.
#>

param(
  [string]$ApiUrl = "http://localhost:3000",
  [string]$Workspace = "./scaffolded-workspace",
  [string]$IqooShareDir = "$env:USERPROFILE\iQOO_Share"
)

$ErrorActionPreference = "Stop"
$Bundle = "bundle.zip"
$Endpoint = "$ApiUrl/api/export"

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "    Agentic Architect - Mobile-to-Desktop Handoff & Auto-Boot     " -ForegroundColor Cyan
Write-Host "    Powered by iQOO 13 / Snapdragon NPU & Qualcomm GenieX        " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Bridge Transfer
$TransferSource = ""
$IqooBundlePath = Join-Path $IqooShareDir $Bundle

if (Test-Path $IqooBundlePath) {
  Write-Host "[BRIDGE] Detected bundle in iQOO Office Kit shared folder: $IqooBundlePath" -ForegroundColor Green
  Copy-Item -Path $IqooBundlePath -Destination $Bundle -Force
  $TransferSource = "iQOO Office Kit Direct Share"
} elseif (Test-Path $Bundle) {
  Write-Host "[BRIDGE] Found existing local $Bundle" -ForegroundColor Green
  $TransferSource = "Local Bundle"
} else {
  Write-Host "[BRIDGE] Requesting workspace bundle via HTTP from $Endpoint ..." -ForegroundColor Yellow
  try {
    Invoke-RestMethod -Uri $Endpoint -Method Post -Body "{}" -ContentType "application/json" -OutFile $Bundle
    $TransferSource = "HTTP Bridge ($Endpoint)"
  } catch {
    Write-Host "[ERROR] Failed to fetch bundle from $Endpoint : $_" -ForegroundColor Red
    exit 1
  }
}

$BundleBytes = (Get-Item $Bundle).Length
Write-Host "[OK] Workspace bundle acquired via $TransferSource ($BundleBytes bytes)" -ForegroundColor Green

# 2. Workspace Unpacking
if (Test-Path $Workspace) {
  Write-Host "[UNPACK] Refreshing target directory: $Workspace ..." -ForegroundColor DarkGray
  Remove-Item -Path $Workspace -Recurse -Force
}

New-Item -ItemType Directory -Path $Workspace -Force | Out-Null
Expand-Archive -Path $Bundle -DestinationPath $Workspace -Force

if ($TransferSource -like "HTTP Bridge*") {
  Remove-Item -Path $Bundle -Force
}

# 3. Dependency Hydration
Write-Host ""
Write-Host "[NPM] Hydrating workspace dependencies (npm install) ..." -ForegroundColor Cyan
if (Get-Command npm -ErrorAction SilentlyContinue) {
  Push-Location $Workspace
  try {
    if (Test-Path "package.json") {
      npm install
      Write-Host "[OK] Dependencies installed successfully." -ForegroundColor Green
    }
  } finally {
    Pop-Location
  }
} else {
  Write-Host "[WARN] npm not found in PATH; skipping npm install." -ForegroundColor Yellow
}

# 4. Verify AI Agent Context Files
Write-Host ""
Write-Host "[CHECK] Verifying AI Agent Manifest Pyramid:" -ForegroundColor Cyan
$Manifests = @("AGENTS.md", ".cursorrules", "CLAUDE.md", "GEMINI.md", "skills\api-contracts.md", "skills\ui-component-system.md", "skills\testing-patterns.md")
foreach ($m in $Manifests) {
  $fPath = Join-Path $Workspace $m
  if (Test-Path $fPath) {
    Write-Host "    [OK] $m" -ForegroundColor Green
  } else {
    Write-Host "    [WARN] $m (missing)" -ForegroundColor Yellow
  }
}

# 5. Agent Priming & IDE Auto-Boot
Write-Host ""
Write-Host "[AGENTS] Priming Multi-Agent System (Cursor, VS Code, Cline, Roo Code, Aider, Claude Code) ..." -ForegroundColor Cyan
$BootedIde = $false

if (Get-Command cursor -ErrorAction SilentlyContinue) {
  Write-Host "[BOOT] Booting Cursor IDE..." -ForegroundColor Green
  Start-Process cursor -ArgumentList $Workspace
  $BootedIde = $true
} elseif (Get-Command code -ErrorAction SilentlyContinue) {
  Write-Host "[BOOT] Booting VS Code..." -ForegroundColor Green
  Start-Process code -ArgumentList $Workspace
  $BootedIde = $true
}

# 6. Summary
Write-Host ""
Write-Host "------------------------------------------------------------------" -ForegroundColor DarkCyan
Write-Host "  Workspace scaffolded and AI-primed successfully!" -ForegroundColor DarkCyan
Write-Host "  Target Directory:  $Workspace" -ForegroundColor White
Write-Host "  Handoff Method:    $TransferSource" -ForegroundColor White
if ($BootedIde) {
  Write-Host "  IDE Boot:          Active (Assistant auto-primed with manifests)" -ForegroundColor Green
} else {
  Write-Host "  Manual Boot:       cd $Workspace ; cursor ." -ForegroundColor White
}
Write-Host "------------------------------------------------------------------" -ForegroundColor DarkCyan
Write-Host ""
