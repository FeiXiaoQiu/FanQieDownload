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

### v1.5.5
- 图源列表默认折叠，点击展开/收起，长按自定义图源可上移/下移排序

### v1.5.4
- 自定义图源选中状态持久化，退出重进保持选中
- R18 图源弹窗倒计时 5 秒后才能确认，按钮左右互换

### v1.5.3
- 自定义 API 背景支持 JSON 解析，随机抽取 `data[].urlsList[].url` 作为背景

### v1.5.2
- FIT 模式改用 SubcomposeAsyncImage 单点加载，加载成功后同时绘制模糊底图与清晰主图

### v1.5.1
- FIT 模式底图改用独立 AsyncImage 分别渲染模糊底图与清晰主图

### v1.5.0
- 背景渲染上提到 MainActivity，Search/Settings 共享同一 painter

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
