# 番茄小说下载器

Web 搜索下载 + Android 客户端「观隅」。

## Web `v2.6.8`

在线：https://fanqie-dl.feixiaoqiu.top/

## Android「观隅」`v1.5.5`

[下载移动端（universal）](https://github.com/FeiXiaoQiu/FanQieDownload/releases/download/v1.5.5/guanyu-1.5.5-universal.apk)

源码 `android/`（Kotlin + Jetpack Compose）。

```bash
cd android
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/
```

### 功能

- 书名搜索、结果分页、书籍简介弹层
- 在线阅读（章节跳转、上一章/下一章）
- TXT 下载（章节范围、断点续传）
- 番茄节点增删改查 + 一键测速（延迟分色）
- 背景切换：栗次元图床 / 妖狐 R18 / 自定义 API
- 背景缩放模式（完整显示 / 铺满屏幕），支持模糊调节
- 一言 Hitokoto 外接 API
- **应用内检查更新 + 直接下载安装**（自动测速选最快下载源）
- **首页新版本更新横幅**，检测到新版本时直接在首页提示下载
- 设置可开关「启动时自动检查更新」
- 电池优化白名单，防止后台下载被系统杀死
- 隐藏功能彩蛋：连续点击「当前版本」10 次解锁妖狐 R18 图源

### 发版

`v*` tag 自动触发 GitHub Actions 构建 `guanyu-{version}-{abi}.apk` 并挂到 Release。

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)。

### v1.2.9
- 下载源管理列表始终可见，无新版时也可增删镜像
- 新版信息 + 下载按钮仅在有可用更新时展示

### v1.2.8
- 在线阅读优化：目录自动定位当前章节 + 章节编号 + 重试按钮
- 底栏页码改为「第 X / Y 章」格式

### v1.2.7
- 修复 detectAbi 对 universal 返回空串的匹配 bug

### v1.2.6
- 下载源排序镜像优先，匹配消息优化

## 目录

```
index.html styles.css app.js browser-client.js charset.json
Speech/                  # 一言
api/                     # Vercel Serverless
server.js                # 本机 Node 服务
android/                 # Android 客户端（观隅）
一键启动.bat / .command
.github/workflows/       # Pages / Release / Android APK
.android/                # Vercel 部署配置
```

## 说明

- 仅供个人学习研究，请遵守版权与平台条款
- 正文节点列表在 `browser-client.js`
- 背景图 API：`https://t.alcy.cc/ycy`；一言：`https://v1.hitokoto.cn/`
- 作者：非小酋
