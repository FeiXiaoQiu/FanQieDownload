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

## Android「观隅」`v1.0.4`

[下载移动端（universal）](https://gh.xmly.dev/https://github.com/FeiXiaoQiu/FanQieDownload/releases/download/v1.0.4/guanyu-1.0.4-universal.apk)

源码 `android/`（Kotlin + Jetpack Compose）。

```bash
cd android
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-*-release.apk
```

### 功能

- 书名搜索、结果分页、书籍简介弹层
- 在线阅读（章节跳转、上一章/下一章）
- TXT 下载（章节范围、断点续传）
- 番茄节点增删改查 + 一键测活（延迟分色）
- 背景切换（默认图床 / 自定义 API / 本地相册）
- 一言 Hitokoto 外接 API
- 检查更新、打开仓库

### 发版

`v*` tag 自动触发 GitHub Actions 构建 `guanyu-{version}-{abi}.apk` 并挂到 Release。

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
