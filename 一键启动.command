#!/bin/bash
cd "$(dirname "$0")"
mkdir -p downloads
if ! command -v node >/dev/null 2>&1; then
  echo "未检测到 Node.js。请先安装：https://nodejs.org/"
  read -r _
  exit 1
fi
export PORT=8787
export HOST=127.0.0.1
echo "正在启动… 浏览器打开 http://127.0.0.1:8787"
echo "关掉本窗口即停止服务。"
node server.js
