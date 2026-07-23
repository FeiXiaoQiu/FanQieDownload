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

## 加速搜索

纯静态站浏览器不能直连番茄节点（无 CORS 头）。公共代理经常超时，**挂 CDN 也解决不了搜索**。

### 方案 A：本机 Node（最稳，不用 Cloudflare）

1. 安装 [Node.js LTS](https://nodejs.org/)
2. 下载仓库 ZIP 解压
3. Windows 双击 `一键启动.bat`，Mac 双击 `一键启动.command`
4. 打开 http://127.0.0.1:8787

### 方案 B：浏览器 CORS 扩展

Chrome / Edge 安装 **Allow CORS** 或 **CORS Unblock**，回到在线页点「测试直连」。

### 方案 C：自建代理（可选）

能打开 Cloudflare 时可用 `cors-worker.js`；或在页面「解决办法」里粘贴任意兼容 `/?url=` 的代理地址。

### 其它

- 节点列表：`browser-client.js` 的 `FIXED_HOSTS`
- 超长书可分章节下载
- 证书过期：Pages → Custom domain → 重新启用 HTTPS

## 限制

- 未配置自建代理时，依赖公共 CORS，可能很慢或失败  
- 第三方节点变更后需改固定列表  


## 本地预览

```bash
python3 -m http.server 5500
```

打开 `http://127.0.0.1:5500`，状态栏应类似「模式：GitHub/静态」。
