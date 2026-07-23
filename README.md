# 番茄小说下载器 v2.2

纯静态可用：书名搜索、ID/链接下载、章节范围、断点续传（localStorage）、取消下载。  
GitHub Pages 部署后自动走固定 5 个正文节点 + CORS 代理。

## 使用

1. 输入书名 →「搜索书名」→ 点选结果  
2. 或粘贴分享链接 / 小说 ID →「开始下载」  
3. 可选填写起始章 / 结束章；默认开启断点续传  
4. 下载中可点「取消下载」  

示例书名：`抽象职校生存手册`

## 在线要快：用 GitHub + Vercel（推荐）

GitHub Pages **只能**托管静态文件，搜索必须经代理；公共代理常挂。  
**代码仍放 GitHub**，用 Vercel 免费托管（含 `/api`）：

1. 打开 https://vercel.com/new → GitHub 登录  
2. 导入本仓库 → Framework 选 **Other** → Deploy  
3. 打开 `https://你的项目.vercel.app`（搜索约 1～3 秒）

可选：Vercel → Domains 绑定 `fanqie-dl.feixiaoqiu.top`。

## GitHub Pages（纯静态，搜索可能慢）

详见 [GITHUB_PAGES.md](./GITHUB_PAGES.md)。

1. 推到 `main`，Pages Source 选 **GitHub Actions**  
2. 适合看页面；搜索依赖公共 CORS，不稳定

## 本地预览

```bash
# 任意静态服务器即可
python3 -m http.server 5500
# 或
npx --yes serve -l 5500 .
```

打开 `http://127.0.0.1:5500`。

## 固定节点

写在 `browser-client.js` 中，失效时改这里即可：

```
http://110.42.57.146:4018
http://81.70.223.143:6897
http://43.143.149.30:8008
http://59.110.160.171:5007
http://103.43.9.59
```

## 目录

```
index.html          # 页面结构
styles.css          # 样式
app.js              # 前端业务
browser-client.js   # 静态下载（固定节点 + CORS）
charset.json        # 字体解码
Speech/             # 一言
.github/workflows/  # Pages 自动部署
```

可选：`server.js` 为本机 Node 代理（非静态部署必需）。

## 搜索要快（代码仍在 GitHub）

| 方式 | 速度 | 说明 |
|------|------|------|
| **Vercel 导入本仓库** | 快 | 推荐在线方案，见上方 |
| 本机 `一键启动.bat` | 快 | 需装 Node，打开 127.0.0.1:8787 |
| 纯 GitHub Pages | 慢/不稳 | 只能走公共 CORS |

## 说明

- 仅供个人学习研究，请遵守版权与平台条款  
- 节点列表在 `browser-client.js`；代理可自建可公共  

