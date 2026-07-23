# GitHub Pages 静态版

本站以**纯静态**为主，无需 Node 即可搜索与下载。

## 原理

1. 页面探测 `/api/health`  
2. **无后端**（Pages 正常情况）→ `browser-client.js`：  
   - 固定 5 个番茄 API 节点  
   - 经公共 CORS 代理请求  
   - 浏览器内拼 TXT 并下载  
   - 章节缓存到 `localStorage`（断点续传）  
3. **若本机另起了 server.js** → 仍可走同源 Node 后端（可选）

## 固定节点

```
http://110.42.57.146:4018
http://81.70.223.143:6897
http://43.143.149.30:8008
http://59.110.160.171:5007
http://103.43.9.59
```

失效时改 `browser-client.js`（以及可选的 `server.js` 种子列表）。

## 自动部署

工作流：`.github/workflows/pages.yml`

1. 推送到 `main` / `master`  
2. Settings → Pages → Source 选 **GitHub Actions**  
3. Actions 中确认 `Deploy GitHub Pages` 成功  
4. 访问 `https://<user>.github.io/<repo>/`

自定义域名：根目录 `CNAME`。

### 发版 zip（可选）

```bash
git tag v2.2.0
git push origin v2.2.0
```

触发 `release-zip.yml`，在 Release 挂静态包。

## 必含文件

| 文件 | 说明 |
|------|------|
| `index.html` | 结构 |
| `styles.css` | 样式 |
| `app.js` | 业务 |
| `browser-client.js` | 静态下载 |
| `charset.json` | 字体解码 |
| `Speech/Speech.txt` | 一言（可选） |

## 加速搜索（强烈推荐）

纯静态站**不能**直连番茄节点（无 CORS 头），必须经代理。公共 allorigins 等经常超时，搜半天没结果是这个原因，**挂 CDN 也解决不了**。

### 免费方案：Cloudflare Worker（约 2 分钟）

1. 打开 [Cloudflare Dashboard](https://dash.cloudflare.com) → Workers & Pages → Create Worker  
2. 把仓库里的 `cors-worker.js` 全文粘贴进去 → Deploy  
3. 复制给你的 `https://xxx.workers.dev`  
4. 打开本站，浏览器控制台（F12）执行：

```js
localStorage.setItem('fq_cors_proxy', 'https://xxx.workers.dev')
location.reload()
```

之后搜索/下载走你的 Worker，通常比公共代理快一个数量级。  
也可在页面注入：`<script>window.FQ_CORS_PROXY='https://xxx.workers.dev'</script>`

### 其它

- 节点列表在 `browser-client.js` 的 `FIXED_HOSTS`  
- 超长书可分章节范围下载  
- 证书过期时：GitHub Pages → Custom domain → 重新勾选 HTTPS  

## 限制

- 未配置自建代理时，依赖公共 CORS，可能很慢或失败  
- 第三方节点变更后需改固定列表  


## 本地预览

```bash
python3 -m http.server 5500
```

打开 `http://127.0.0.1:5500`，状态栏应类似「模式：GitHub/静态」。
