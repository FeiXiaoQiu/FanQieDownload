# 番茄小说下载器 · Android

Kotlin + Jetpack Compose 本地客户端：搜索、简介、TXT 下载；设置中可配置番茄节点 API 与一言 API。

## 环境

- Android Studio Hedgehog / Ladybug 或更新
- JDK 17
- minSdk 26 / targetSdk 34

## 本地编译

```bash
cd android
./gradlew :app:assembleDebug
```

APK 输出：

`app/build/outputs/apk/debug/app-debug.apk`

Release（debug 签名，便于 CI）：

```bash
./gradlew :app:assembleRelease
```

## 功能

- 主页：背景图 `https://t.alcy.cc/ycy`，黑底半透明叠层 + 圆角白卡片搜索区
- 搜索 / 简介 / 单本下载（起止章、断点续传）
- TXT 写入系统 Download/`FanQieDownload`
- 设置：番茄节点增删改、测活、恢复默认；一言 URL（默认 Hitokoto）

## 节点协议

与 Web 一致：

- `GET {base}/search?query=&page=0`
- `GET {base}/info?book_id=`
- `GET {base}/catalog?book_id=`
- `GET {base}/content?item_id=`

## CI

仓库工作流 `.github/workflows/android.yml`：

- `workflow_dispatch` 手动构建并上传 APK artifact
- 推送 `v*` tag 时构建并挂到 GitHub Release

## 说明

仅供个人学习研究，请遵守版权与平台条款。
