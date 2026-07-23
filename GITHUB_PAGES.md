# GitHub Pages 静态版

本仓库可直接部署到 GitHub Pages（纯静态，无需 Node）。

## 原理

1. 页面自动探测 `/api/health`
2. **有 Node 后端** → 走服务端下载（宝塔/VPS）
3. **无后端（GitHub Pages）** → 启用 `browser-client.js`：
   - 固定 5 个番茄 API 节点
   - 经 `api.allorigins.win` 做 CORS 代理
   - 浏览器内拼 TXT 并下载
   - 章节缓存到 `localStorage`（断点续传）

## 固定节点

```
http://110.42.57.146:4018
http://81.70.223.143:6897
http://43.143.149.30:8008
http://59.110.160.171:5007
http://103.43.9.59
```

节点失效时需更新 `browser-client.js` 与 `server.js` 中的列表。

## 部署步骤（推荐：Actions 自动部署）

仓库已带工作流：`.github/workflows/pages.yml`

1. 推送到 GitHub（`main` 或 `master`）
2. Settings → **Pages** → **Build and deployment** → Source 选 **GitHub Actions**
3. 打开 Actions 看 `Deploy GitHub Pages` 是否成功
4. 访问：`https://<user>.github.io/<repo>/`

自定义域名：在仓库根目录放 `CNAME`（工作流会一并拷进站点）。

### 备选：Deploy from branch

若不用 Actions，Source 选 branch，目录 `/ (root)`，同样会托管根目录静态文件。

### 发版压缩包

推送 tag 会触发 `.github/workflows/release-zip.yml`，自动挂 zip 到 Release：

```bash
git tag v2.2.0
git push origin v2.2.0
```

## 必含文件

| 文件 | 说明 |
|------|------|
| `index.html` | 页面结构 |
| `styles.css` | 样式 |
| `app.js` | 前端业务 |
| `browser-client.js` | 静态下载逻辑 |
| `charset.json` | 字体解码 |
| `Speech/Speech.txt` | 一言（可选） |

`server.js` 在 Pages 上不会运行，可保留供他人本地/面板使用。

## 限制说明

- 依赖公共 CORS 代理，偶尔会慢或不稳
- 长书建议在 Node/宝塔版下载更稳
- 第三方节点变更后需改代码里的固定列表

## 本地预览静态模式

```bash
# 不要起 server.js，用任意静态服务器
npx --yes serve -l 5500 .
# 或
python3 -m http.server 5500
```

打开 `http://127.0.0.1:5500`，页面应显示「模式：GitHub/静态」。
