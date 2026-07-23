@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist downloads mkdir downloads
where node >nul 2>nul
if errorlevel 1 (
  echo 未检测到 Node.js。请先安装：https://nodejs.org/
  echo 安装时勾选 Add to PATH，装完重新打开本窗口。
  pause
  exit /b 1
)
echo 正在启动番茄下载器...
echo 浏览器打开: http://127.0.0.1:8787
echo 关掉本窗口即停止服务。
echo.
set PORT=8787
set HOST=127.0.0.1
node server.js
pause
