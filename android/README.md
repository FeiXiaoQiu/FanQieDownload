# 观隅 · Android

Kotlin + Jetpack Compose 本地客户端：搜索、简介、在线阅读、TXT 下载；设置中可配置下载节点、一言与背景。

## 环境

- Android Studio Hedgehog / Ladybug 或更新
- JDK 17
- minSdk 26 / targetSdk 34

## 本地编译

```bash
cd android
./gradlew :app:assembleRelease
```

Release 使用仓库内固定密钥（`keystore/` + `keystore.properties`）。**禁止重新生成密钥。**

按架构拆分产物（另含 universal）：

- `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
- `app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
- `app/build/outputs/apk/release/app-universal-release.apk`

CI 发布命名（应用显示名仍为「观隅」；GitHub 附件名用拼音）：

- `guanyu-{version}-arm64-v8a.apk`
- `guanyu-{version}-armeabi-v7a.apk`
- `guanyu-{version}-universal.apk`

## 功能

- 开屏约 3 秒（动态）；后台预拉背景图与一言
- 主页：背景可配置（默认图床 / 自定义 API / 固定图），玻璃拟态卡片
- 搜索分页 / 书籍简介 / 在线阅读 / 单本下载
- 设置：下载节点（优先番茄 API）、一言、背景、关于（作者 / 检查更新 / 仓库）

## CI

仓库工作流 `.github/workflows/android.yml`：

- `workflow_dispatch` 手动构建并上传 APK artifact
- 推送 `v*` tag 时构建并挂到 GitHub Release
