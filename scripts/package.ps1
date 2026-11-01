# Joyo 打包脚本（docs/10-architecture/overview.md 第 5 节）
# 1) 前端构建  2) 后端 jar  3) jlink 裁剪 JRE  4) electron-builder 出安装包
# 用法：powershell -ExecutionPolicy Bypass -File scripts/package.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

Step '1/5 前端构建（vite）'
pnpm build:frontend
if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }

Step '2/5 主进程构建（tsc）'
pnpm build:desktop
if ($LASTEXITCODE -ne 0) { throw 'desktop build failed' }

Step '3/5 后端 jar（mvn package）'
Push-Location backend
try {
  if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { mvn.cmd -q -DskipTests package }
  else { mvn -q -DskipTests package }
  if ($LASTEXITCODE -ne 0) { throw 'backend package failed' }
} finally { Pop-Location }

$jar = Join-Path $root 'backend/target/joyo-backend.jar'
if (-not (Test-Path $jar)) { throw "backend jar not found: $jar" }

Step '4/5 渲染产物就位 + jlink 裁剪 JRE'
$renderer = Join-Path $root 'desktop/dist-electron/renderer'
if (Test-Path $renderer) { Remove-Item $renderer -Recurse -Force }
Copy-Item (Join-Path $root 'frontend/dist') $renderer -Recurse

$runtime = Join-Path $root 'backend-runtime'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
try {
  $deps = & jdeps --print-module-deps --ignore-missing-deps --multi-release 21 $jar 2>$null
  if ($LASTEXITCODE -eq 0 -and $deps) {
    $modules = ($deps | Select-Object -Last 1).Trim()
    & jlink --add-modules "$modules,jdk.management,jdk.unsupported" --output $runtime --strip-debug --no-man-pages --no-header-files --compress=2
    Write-Host "JRE ready: $runtime" -ForegroundColor Green
  } else {
    Write-Host 'jdeps failed, skip JRE (system java will be used)' -ForegroundColor Yellow
  }
} catch {
  Write-Host "jlink unavailable, skip: $_" -ForegroundColor Yellow
}

Step '5/5 electron-builder'
Push-Location desktop
try {
  pnpm package
  if ($LASTEXITCODE -ne 0) { throw 'electron-builder failed' }
} finally { Pop-Location }

Write-Host "`nDone, artifacts in release/" -ForegroundColor Green