$ErrorActionPreference = "Stop"

# Always use the local project virtual environment.
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonExe = Join-Path $projectRoot ".venv\\Scripts\\python.exe"
$port = 1601

function Get-ProcessInfo([int]$processId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
}

if (-not (Test-Path $pythonExe)) {
    throw "Missing local virtual environment: $pythonExe. Run 'uv sync' in reactor-tool first."
}

Push-Location $projectRoot
try {
    Remove-Item Env:VIRTUAL_ENV -ErrorAction SilentlyContinue

    # Single-process startup keeps the runtime environment stable.
    $env:ENV = "prod"
    $env:PYTHONIOENCODING = "utf-8"

    if (-not $env:SKILL_PYTHON_BIN) {
        $env:SKILL_PYTHON_BIN = $pythonExe
    }

    $defaultFileSavePath = Join-Path $projectRoot "skilloutput"
    $hasHttpFileServerUrl = $env:FILE_SERVER_URL -and $env:FILE_SERVER_URL -match '^https?://'

    if (-not $env:FILE_SAVE_PATH) {
        if ($env:FILE_SERVER_URL -and -not $hasHttpFileServerUrl) {
            $env:FILE_SAVE_PATH = $env:FILE_SERVER_URL
        } else {
            $env:FILE_SAVE_PATH = $defaultFileSavePath
        }
    }

    if (-not $hasHttpFileServerUrl) {
        $env:FILE_SERVER_URL = "http://127.0.0.1:$port/v1/file_tool"
    }

    # FILE_SAVE_PATH 负责本地落盘目录，FILE_SERVER_URL 必须保持为可访问的 HTTP 地址。
    New-Item -ItemType Directory -Force -Path $env:FILE_SAVE_PATH | Out-Null

    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $listenerProcess = Get-ProcessInfo $listener.OwningProcess
        $commandLine = if ($listenerProcess) { [string]$listenerProcess.CommandLine } else { "" }
        $commandLineLower = $commandLine.ToLowerInvariant()
        $isCurrentServer = $false
        if ($listenerProcess) {
            $isCurrentServer = ($listenerProcess.Name -ieq "python.exe" -and $commandLineLower.Contains("server.py") -and $commandLineLower.Contains("--workers 1"))
        }

        if ($isCurrentServer) {
            Write-Host "reactor-tool is already running on port $port (PID $($listener.OwningProcess))."
            Write-Host "Close the existing reactor-tool window first if you want to restart it."
            exit 0
        }

        if ([string]::IsNullOrWhiteSpace($commandLine)) {
            $commandLine = "unknown process"
        }
        throw "Port $port is already in use by PID $($listener.OwningProcess): $commandLine"
    }

    & $pythonExe "server.py" "--workers" "1"
}
finally {
    Pop-Location
}
