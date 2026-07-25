# 番茄小说下载器

Web 搜索下载 + Android 客户端「观隅」。

## Web `v2.4.0`

在线：https://fanqie-dl.feixiaoqiu.top/

书名搜索、简介预览、ID/链接下载、章节范围、断点续传、取消下载。  
背景图床、一言、正文节点可在设置面板配置。

### 本机运行

```bash
node server.js
# 浏览器打开 http://127.0.0.1:8787
```

### 在线部署（Vercel）

1. [vercel.com/new](https://vercel.com/new) 导入本仓库
2. Framework 选 **Other**，直接 Deploy
3. 国内建议绑定自有域名

## Android「观隅」`v1.2.5`

[下载移动端（universal）](https://github.com/FeiXiaoQiu/FanQieDownload/releases/download/v1.2.5/guanyu-1.2.5-universal.apk)

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
- 背景切换：栗次元图床 / 妖狐 R18 / 自定义 API / 本地相册
- 背景缩放模式（完整显示 / 铺满屏幕），本地图片支持模糊调节
- 一言 Hitokoto 外接 API
- **应用内检查更新 + 直接下载安装**（自动测速选最快下载源）
- 电池优化白名单，防止后台下载被系统杀死

### 发版

`v*` tag 自动触发 GitHub Actions 构建 `guanyu-{version}-{abi}.apk` 并挂到 Release。

## 更新日志

### v1.1.9
- 修复「检查出更新但不弹出下载选项」：`matchingAsset` 为 null 时回退到第一条 APK 资产

### v1.1.8
- 新增「省电设置」卡片，一键关闭电池优化防后台被杀
- 下载前对所有源 HEAD 测速，自动选最快的下载
- 修复下载「未知错误」（NetworkOnMainThreadException → withContext(IO)）
- 修复铺满屏幕模式下背景仍然模糊的问题

### v1.1.7
- 修复下载 API 跑在主线程导致「未知错误」
- 下载全部移入 `withContext(Dispatchers.IO)`

### v1.1.6
- 铺满屏幕 (CROP) 模式下只显示清晰 Crop 层，不再叠加模糊

### v1.1.5
- 背景设置拆为「接口背景」「本地图片」两张卡片
- 模糊滑块仅在本地图片模式下生效，默认 10dp
- 修复检查更新静默模式阻塞手动点击
- 网页下载链改用 GitHub 直链

### v1.1.4
- 新增背景缩放模式（完整显示 / 铺满屏幕），双页面同步
- 自定义图片背景可调节模糊程度 (0~48dp)

### v1.1.3
- 应用内检查更新 + 直接下载安装 APK
- 下载源配置（原链 / 镜像源 / 自定义模板）
- 设备 ABI 自动匹配
- SearchScreen 背景同步为双层渲染

### v1.1.2 及更早
- 栗次元图床 / 妖狐 R18 背景
- 本地图片裁剪上传
- 节点测速（延迟分色）
- 在线阅读器
- 一言 Hitokoto

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
