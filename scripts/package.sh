#!/usr/bin/env bash
# Joyo 打包脚本（docs/10-architecture/overview.md 第 5 节）
# 1) 前端构建  2) 后端 jar  3) jlink 裁剪 JRE  4) electron-builder 出安装包
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

step() { printf '\n==> %s\n' "$1"; }

step '1/5 前端构建（vite）'
pnpm build:frontend

step '2/5 主进程构建（tsc）'
pnpm build:desktop

step '3/5 后端 jar（mvn package）'
(cd backend && mvn -q -DskipTests package)
jar="$root/backend/target/joyo-backend.jar"
[ -f "$jar" ] || { echo "未找到后端 jar: $jar" >&2; exit 1; }

step '4/5 渲染产物就位 + jlink 裁剪 JRE'
rm -rf "$root/desktop/dist-electron/renderer"
cp -R "$root/frontend/dist" "$root/desktop/dist-electron/renderer"

runtime="$root/backend-runtime"
rm -rf "$runtime"
if command -v jdeps >/dev/null 2>&1 && command -v jlink >/dev/null 2>&1; then
  modules="$(jdeps --print-module-deps --ignore-missing-deps --multi-release 21 "$jar" 2>/dev/null | tail -1 || true)"
  if [ -n "$modules" ]; then
    jlink --add-modules "$modules,jdk.management,jdk.unsupported" --output "$runtime" \
      --strip-debug --no-man-pages --no-header-files --compress=2
    echo "JRE 已裁剪到 $runtime"
  fi
else
  echo 'jlink 不可用，跳过 JRE 裁剪（安装包将使用系统 Java）'
fi

step '5/5 electron-builder 出安装包'
(cd desktop && pnpm package)

printf '\n完成，产物在 release/ 目录\n'
