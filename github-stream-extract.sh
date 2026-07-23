#!/usr/bin/env bash
# GitHub 流式解压（不写入 dist 压缩包）
# 用法：
#   ./github-stream-extract.sh <zip或tar.gz的URL> [目标目录]
# 示例：
#   ./github-stream-extract.sh https://github.com/USER/REPO/archive/refs/heads/main.tar.gz ./site
#   ./github-stream-extract.sh https://github.com/USER/REPO/releases/download/v2.2/fanqie-dl-github-pages.zip ./pages

set -euo pipefail

URL="${1:-}"
DEST="${2:-.}"

if [[ -z "$URL" ]]; then
  echo "用法: $0 <zip|tar.gz URL> [目标目录]" >&2
  exit 1
fi

mkdir -p "$DEST"
cd "$DEST"

# tar.gz / tarball：真正流式，边下边解
if [[ "$URL" == *.tar.gz ]] || [[ "$URL" == *.tgz ]] || [[ "$URL" == */tarball/* ]] || [[ "$URL" == *archive/refs/heads/* ]]; then
  echo "流式解压 tar: $URL -> $DEST"
  curl -fsSL "$URL" | tar -xzf -
  echo "完成"
  exit 0
fi

# zip：标准 unzip 不支持 stdin，用临时文件；有 bsdtar 则流式
if command -v bsdtar >/dev/null 2>&1; then
  echo "流式解压 zip (bsdtar): $URL -> $DEST"
  curl -fsSL "$URL" | bsdtar -xvf -
  echo "完成"
  exit 0
fi

TMP="$(mktemp /tmp/fq-XXXXXX.zip)"
trap 'rm -f "$TMP"' EXIT
echo "下载 zip 后解压: $URL -> $DEST"
curl -fsSL "$URL" -o "$TMP"
unzip -o "$TMP"
echo "完成"
