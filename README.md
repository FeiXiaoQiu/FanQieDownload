# 番茄小说下载器 v2.4

书名搜索、ID/链接下载、章节范围、断点续传、取消下载。  
推荐：**GitHub 存代码 + Vercel 部署**（搜索/封面走 `/api`）。

## 使用

1. 输入书名 →「搜索书名」→ 点选结果  
2. 或粘贴分享链接 / 小说 ID →「开始下载」  
3. 可选起始章 / 结束章；默认断点续传  

示例：`抽象职校生存手册`

## 在线部署（Vercel）

1. [vercel.com/new](https://vercel.com/new) 导入本仓库  
2. Framework 选 **Other**，直接 Deploy  
3. 国内建议绑定自有域名（Settings → Domains），CNAME 到 Vercel  

仓库已含：`api/health.js` `api/search.js` `api/proxy.js` + `vercel.json`

## 本机运行

```bash
# 需 Node.js >= 18
node server.js
# 或双击 一键启动.bat / 一键启动.command
```

浏览器打开 `http://127.0.0.1:8787`。

## 目录

```
index.html styles.css app.js browser-client.js charset.json
Speech/                 # 一言
api/                    # Vercel Serverless
server.js               # 本机完整 Node 服务
一键启动.bat / .command
.github/workflows/      # Pages / Release
```

## 说明

- 仅供个人学习研究，请遵守版权与平台条款  
- 正文节点列表在 `browser-client.js`  
