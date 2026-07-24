# 飞小秋阅 · Android

Kotlin + Jetpack Compose 本地客户端：搜索、简介、在线阅读、TXT 下载；设置中可配置下载节点、一言与背景。

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

Release / Debug 使用仓库内固定密钥（`keystore/` + `keystore.properties`），从 1.0.2 起各版本签名一致，debug 与 release 分开：

```bash
./gradlew :app:assembleRelease
./gradlew :app:assembleDebug
```

## 功能

- 主页：背景可配置（默认图床 / 自定义 API / 固定图），黑底 + 圆角白卡
- 搜索分页 / 书籍简介 / 在线阅读 / 单本下载（起止章、断点续传）
- TXT 写入系统 Download/`FanQieDownload`
- 设置：下载节点列表（填入即用，删掉即停）、一言、背景

## CI

仓库工作流 `.github/workflows/android.yml`：

- `workflow_dispatch` 手动构建并上传 APK artifact
- 推送 `v*` tag 时构建并挂到 GitHub Release

## 说明

仅供个人学习研究，请遵守版权与平台条款。
