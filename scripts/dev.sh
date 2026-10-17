#!/usr/bin/env bash
# Joyo 开发环境一键启动（bash 版，适合 Git Bash）：后端 + 前端 + Electron
# 用法：bash scripts/dev.sh
# 退出方式：关闭 Joyo 窗口（脚本自动清理后端与前端进程）
# 已在运行的组件会被复用，不会重复启动

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_URL="http://127.0.0.1:18080"
VITE_URL="http://localhost:5173"

backend_pid=""
vite_pid=""

kill_tree() {
  local pid="$1"
  if uname | grep -qi "mingw\|msys\|cygwin"; then
    # Git Bash 下用 taskkill 杀进程树，避免残留 java/node 子进程
    taskkill //PID "$pid" //T //F >/dev/null 2>&1 || true
  else
    kill "$pid" 2>/dev/null || true
  fi
}

cleanup() {
  echo "[dev] 清理后台进程..."
  [ -n "$vite_pid" ] && kill_tree "$vite_pid"
  [ -n "$backend_pid" ] && kill_tree "$backend_pid"
  echo "[dev] 已清理"
}
trap cleanup EXIT

backend_ready() {
  curl -s -m 2 -H "X-Joyo-Token: dev-token" "$BACKEND_URL/api/v1/health" | grep -q '"code":0'
}

vite_ready() {
  curl -s -m 2 -o /dev/null -w "%{http_code}" "$VITE_URL" | grep -q 200
}

# 1. 后端
if backend_ready; then
  echo "[dev] 后端已在运行，复用"
else
  echo "[dev] 启动后端（mvn spring-boot:run）..."
  (cd "$ROOT/backend" && mvn spring-boot:run >/dev/null 2>&1) &
  backend_pid=$!
  ok=0
  for _ in $(seq 1 120); do
    if backend_ready; then ok=1; break; fi
    sleep 0.5
  done
  [ "$ok" = "1" ] || { echo "[dev] 后端在 60s 内未就绪"; exit 1; }
  echo "[dev] 后端就绪"
fi

# 2. 前端
if vite_ready; then
  echo "[dev] 前端已在运行，复用"
else
  echo "[dev] 启动前端（vite dev）..."
  (cd "$ROOT/frontend" && pnpm dev >/dev/null 2>&1) &
  vite_pid=$!
  ok=0
  for _ in $(seq 1 60); do
    if vite_ready; then ok=1; break; fi
    sleep 0.5
  done
  [ "$ok" = "1" ] || { echo "[dev] 前端在 30s 内未就绪"; exit 1; }
  echo "[dev] 前端就绪"
fi

# 3. Electron（前台运行，关窗即退出并清理）
echo "[dev] 启动桌面窗口（关闭窗口即退出并清理）..."
export JOYO_DEV_BACKEND_URL="$BACKEND_URL"
export VITE_DEV_SERVER_URL="$VITE_URL"
cd "$ROOT/desktop" && pnpm dev
