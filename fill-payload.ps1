param(
    [string]$CertDir = "C:\Users\WWZ\Downloads\owwzo-top-cert"
)

$ErrorActionPreference = "Stop"

$repo = $PSScriptRoot
$bundle = Join-Path $repo "docs\dev-ops\ubuntu\server-bundle"

$jarSource = Join-Path $repo "Reactor-agent-app\target\Reactor-agent-app.jar"
$uiDistSource = Join-Path $repo "ui\dist"
$toolSource = Join-Path $repo "reactor-tool"
$certPemSource = Join-Path $CertDir "_.owwzo.top.pem"
$certKeySource = Join-Path $CertDir "_.owwzo.top.key"

$payloadBackend = Join-Path $bundle "payload\backend"
$payloadUi = Join-Path $bundle "payload\ui-dist"
$payloadTool = Join-Path $bundle "payload\reactor-tool"
$payloadCerts = Join-Path $bundle "payload\certs"

function Assert-PathExists {
    param(
        [string]$TargetPath,
        [string]$Label
    )

    if (!(Test-Path $TargetPath)) {
        throw "$Label not found: $TargetPath"
    }
}

Assert-PathExists -TargetPath $jarSource -Label "Backend jar"
Assert-PathExists -TargetPath $uiDistSource -Label "Frontend dist"
Assert-PathExists -TargetPath $toolSource -Label "reactor-tool directory"
Assert-PathExists -TargetPath $certPemSource -Label "Certificate pem"
Assert-PathExists -TargetPath $certKeySource -Label "Certificate key"

New-Item -ItemType Directory -Force -Path $payloadBackend | Out-Null
New-Item -ItemType Directory -Force -Path $payloadUi | Out-Null
New-Item -ItemType Directory -Force -Path $payloadTool | Out-Null
New-Item -ItemType Directory -Force -Path $payloadCerts | Out-Null

Write-Host "Cleaning old payload..."
Get-ChildItem $payloadBackend -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem $payloadUi -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem $payloadTool -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem $payloadCerts -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Copying backend jar..."
Copy-Item $jarSource (Join-Path $payloadBackend "Reactor-agent-app.jar") -Force

Write-Host "Copying frontend dist..."
Copy-Item (Join-Path $uiDistSource "*") $payloadUi -Recurse -Force

Write-Host "Copying reactor-tool (lean bundle)..."

$toolIncludePaths = @(
    ".env_template",
    ".gitignore",
    ".python-version",
    "pyproject.toml",
    "uv.lock",
    "README.md",
    "server.py",
    "start.sh",
    "start.ps1",
    "reactor_tool",
    "tests"
)

foreach ($relativePath in $toolIncludePaths) {
    $sourcePath = Join-Path $toolSource $relativePath
    if (Test-Path $sourcePath) {
        Copy-Item $sourcePath $payloadTool -Recurse -Force
    }
}

Write-Host "Copying certificates..."
Copy-Item $certPemSource (Join-Path $payloadCerts "_.owwzo.top.pem") -Force
Copy-Item $certKeySource (Join-Path $payloadCerts "_.owwzo.top.key") -Force

Write-Host ""
Write-Host "Payload fill completed."
Write-Host "Bundle directory: $bundle"
