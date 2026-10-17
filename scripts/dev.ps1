# Joyo 开发环境一键启动：后端 + 前端 + Electron 桌面窗口
# 用法：仓库根目录执行  powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
# 退出方式：关闭 Joyo 窗口（脚本自动清理后端与前端进程）
# 已在运行的组件会被复用，不会重复启动

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# 工具路径：优先本机已知位置，找不到回退 PATH
$mvn = "D:\dev\tool\apache-maven-3.9.8\bin\mvn.cmd"
if (-not (Test-Path $mvn)) { $mvn = "mvn" }
$pnpm = "C:\Users\Administrator\AppData\Roaming\npm\pnpm.cmd"
if (-not (Test-Path $pnpm)) { $pnpm = "pnpm" }

function Test-BackendReady {
    try {
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/v1/health" `
            -Headers @{ "X-Joyo-Token" = "dev-token" } -TimeoutSec 2
        return $r.code -eq 0
    } catch { return $false }
}

function Test-ViteReady {
    try {
        return (Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200
    } catch { return $false }
}

$started = @()

# 1. 后端
if (Test-BackendReady) {
    Write-Host "[dev] 后端已在运行，复用" -ForegroundColor Yellow
} else {
    Write-Host "[dev] 启动后端（mvn spring-boot:run）..."
    $started += Start-Process -FilePath $mvn -ArgumentList "spring-boot:run" `
        -WorkingDirectory "$root\backend" -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(60)
    while (-not (Test-BackendReady)) {
        if ((Get-Date) -gt $deadline) { throw "后端在 60s 内未就绪，请检查窗口 1 的 mvn 输出" }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "[dev] 后端就绪" -ForegroundColor Green
}

# 2. 前端
if (Test-ViteReady) {
    Write-Host "[dev] 前端已在运行，复用" -ForegroundColor Yellow
} else {
    Write-Host "[dev] 启动前端（vite dev）..."
    $started += Start-Process -FilePath $pnpm -ArgumentList "dev" `
        -WorkingDirectory "$root\frontend" -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(30)
    while (-not (Test-ViteReady)) {
        if ((Get-Date) -gt $deadline) { throw "前端在 30s 内未就绪" }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "[dev] 前端就绪" -ForegroundColor Green
}

# 3. Electron（前台运行，关窗即退出并清理）
Write-Host "[dev] 启动桌面窗口（关闭窗口即退出并清理）..." -ForegroundColor Cyan
$env:JOYO_DEV_BACKEND_URL = "http://127.0.0.1:18080"
$env:VITE_DEV_SERVER_URL = "http://localhost:5173"
try {
    & $pnpm --dir "$root\desktop" dev
} finally {
    Write-Host "[dev] 清理后台进程..."
    foreach ($p in $started) {
        if ($p -and -not $p.HasExited) {
            taskkill /PID $p.Id /T /F 2>$null | Out-Null
        }
    }
    Write-Host "[dev] 已清理" -ForegroundColor Green
}
