@echo off
setlocal

REM 以脚本自身所在目录为根，避免写死绝对路径（目录改名后仍可用）。
set "PROJECT_ROOT=%~dp0"

REM API 多进程；bash/code_execution 由 start-split 另起 :1602 workers=1
set "CUTE_TOOL_WORKERS=5"
start "cute-tool-split" powershell -NoProfile -ExecutionPolicy Bypass -NoExit -File "%PROJECT_ROOT%cute-tool\start-split.ps1"

endlocal
