# 可选：用 Vercel 加速在线搜索

GitHub Pages 只托管静态文件。要把搜索变快，把本仓库部署到 **Vercel**（从 GitHub 一键导入）：

1. 打开 https://vercel.com/new
2. 用 GitHub 登录，导入 `FeiXiaoQiu/FanQieDownload`
3. Framework Preset 选 **Other**，直接 Deploy
4. 得到 `https://xxx.vercel.app` —— 搜索走同源 `/api/search` 与 `/api/proxy`，通常 1～3 秒

自定义域名可在 Vercel 项目 Settings → Domains 绑定（比 Pages 证书更好续）。

仓库已含：
- `api/health.js` `api/search.js` `api/proxy.js`
- `vercel.json`
