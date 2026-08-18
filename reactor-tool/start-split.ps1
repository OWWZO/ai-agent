$ErrorActionPreference = "Stop"

# 拆分启动：sandbox(1602, workers=1) + api(1601, 多 worker)
# Java 仍只配 code_interpreter_url=http://127.0.0.1:1601

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonExe = Join-Path $projectRoot ".venv\Scripts\python.exe"
$apiPort = 1601
$sandboxPort = 1602
$apiWorkersText = if ([string]::IsNullOrWhiteSpace($env:REACTOR_TOOL_WORKERS)) { "4" } else { $env:REACTOR_TOOL_WORKERS.Trim() }

if (-not (Test-Path $pythonExe)) {
    throw "Missing local virtual environment: $pythonExe. Run 'uv sync' in reactor-tool first."
}

function Assert-PortFree([int]$port) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        throw "Port $port is already in use by PID $($listener.OwningProcess)"
    }
}

Assert-PortFree $apiPort
Assert-PortFree $sandboxPort

Push-Location $projectRoot
try {
    Remove-Item Env:VIRTUAL_ENV -ErrorAction SilentlyContinue
    $env:ENV = "prod"
    $env:PYTHONIOENCODING = "utf-8"
    if (-not $env:SKILL_PYTHON_BIN) { $env:SKILL_PYTHON_BIN = $pythonExe }

    $defaultFileSavePath = Join-Path $projectRoot "skilloutput"
    if (-not $env:FILE_SAVE_PATH) { $env:FILE_SAVE_PATH = $defaultFileSavePath }
    New-Item -ItemType Directory -Force -Path $env:FILE_SAVE_PATH | Out-Null
    if (-not ($env:FILE_SERVER_URL -and $env:FILE_SERVER_URL -match '^https?://')) {
        $env:FILE_SERVER_URL = "http://127.0.0.1:$apiPort/v1/file_tool"
    }

    $env:REACTOR_SANDBOX_URL = "http://127.0.0.1:$sandboxPort"

    Write-Host "Starting sandbox on :$sandboxPort (workers=1) ..."
    $sandboxEnv = $env:REACTOR_TOOL_ROLE
    $env:REACTOR_TOOL_ROLE = "sandbox"
    Start-Process -FilePath $pythonExe -ArgumentList @(
        "server.py", "--host", "127.0.0.1", "--port", "$sandboxPort", "--workers", "1", "--role", "sandbox"
    ) -WorkingDirectory $projectRoot -WindowStyle Normal

    Start-Sleep -Seconds 2

    Write-Host "Starting api on :$apiPort (workers=$apiWorkersText) ..."
    $env:REACTOR_TOOL_ROLE = "api"
    & $pythonExe "server.py" "--host" "0.0.0.0" "--port" "$apiPort" "--workers" $apiWorkersText "--role" "api"
}
finally {
    if ($null -ne $sandboxEnv) { $env:REACTOR_TOOL_ROLE = $sandboxEnv } else { Remove-Item Env:REACTOR_TOOL_ROLE -ErrorAction SilentlyContinue }
    Pop-Location
}
