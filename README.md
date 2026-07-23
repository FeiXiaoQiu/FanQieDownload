# 番茄小说下载器 v2.2

双模式：

| 模式 | 场景 | 说明 |
|------|------|------|
| **Node 动态站** | 宝塔 / VPS / 本机 | `node server.js`，完整能力 + 自动发现节点 |
| **GitHub 静态** | GitHub Pages | 无需后端，固定 5 节点 + CORS 代理 |

## 一分钟上手（Node / 宝塔）

```bash
# 需要 Node.js >= 18（宝塔 24.x 可用）
./start.sh
# 或
node server.js
```

浏览器打开：`http://127.0.0.1:8787`

1. 输入书名 → 点「搜索书名」→ 点结果  
2. 或粘贴分享链接 / 小说 ID →「开始下载」  
3. 可选：起始章 / 结束章；默认开启断点续传  
4. 下载中可点「取消下载」；再次下载会跳过已缓存章节  

## 使用方法

| 操作 | 说明 |
|------|------|
| 书名搜索 | 优先走第三方节点 search，失败回退官方 API |
| ID / 链接 | 支持 `book_id=`、`fanqienovel.com/page/xxx`、纯数字 ID |
| 章节范围 | 起始章、结束章（从 1 起算）；都空=全书 |
| 断点续传 | 章节缓存到 `downloads/cache/{book_id}/`，中断后续传 |
| 取消 | 请求停止当前任务；已下章节保留缓存 |
| 节点状态 | 页面下方显示在线节点数；接口 `/api/nodes` |

## 宝塔面板部署（推荐域名反代）

1. **安装 Node.js ≥ 18**（软件商店或命令行）  
2. 解压本包到例如 `/www/wwwroot/fanqie-dl`  
3. 进入目录执行：

```bash
cd /www/wwwroot/fanqie-dl
chmod +x start.sh
# 建议用 PM2 守护
npm install -g pm2
pm2 start server.js --name fanqie-dl
pm2 save
```

4. **网站 → 添加站点 → 绑定你的域名**（解析 A 记录到服务器）  
5. 站点设置 → **反向代理** → 目标：`http://127.0.0.1:8787`  
6. 开启 HTTPS（可选，Let's Encrypt）  

用户只访问域名，**不必在地址栏写端口**。`8787` 只给本机 Nginx 转发。

### 防火墙

若外网要直连 `IP:8787`，再放行端口；走域名反代时通常只开 80/443。

## 环境变量（可选）

| 变量 | 默认 | 说明 |
|------|------|------|
| `PORT` | `8787` | 本机监听端口 |
| `HOST` | `0.0.0.0` | 监听地址 |
| `MAX_WORKERS` | `6` | 章节并发 |
| `CHAPTER_DELAY` | `0.05` | 章间隔（秒） |
| `CONTENT_API_HOSTS` | 内置种子 | 逗号分隔节点 |
| `NODE_PROBE_INTERVAL` | `300000` | 探活间隔 ms |
| `NODE_DISCOVER_INTERVAL` | `1800000` | 发现间隔 ms |

## API

| 接口 | 说明 |
|------|------|
| `GET /api/search?q=书名` | 搜索 |
| `GET /api/book/{book_id}` | 书籍信息 |
| `GET /api/download?book_id=&start_chapter=&end_chapter=&resume=1` | 创建任务 |
| `GET /api/job/{id}` | 进度 |
| `POST /api/job/{id}/cancel` | 取消 |
| `GET /api/job/{id}/file` | 下载 TXT |
| `GET /api/nodes` | 节点健康 |
| `GET /api/nodes/refresh` | 重新发现节点 |
| `GET /api/health` | 健康检查 |

## 目录说明

```
server.js           # 主服务（Node / 宝塔）
index.html          # 页面结构
styles.css          # 样式
app.js              # 前端业务
browser-client.js   # GitHub 静态模式客户端
charset.json        # 字体解码表
Speech/             # 一言语料
start.sh            # 启动脚本
package.json        # npm start
downloads/          # 生成的 TXT 与章节缓存（运行后产生）
```

## GitHub Pages

详见 [GITHUB_PAGES.md](./GITHUB_PAGES.md)。

要点：推送到仓库根目录开启 Pages 即可；页面会自动走静态模式（`browser-client.js` + 固定 5 节点）。

## 说明

- 仅供个人学习研究，请遵守版权与平台条款  
- 静态模式依赖公共 CORS 代理与第三方节点可用性  
- `server.py` 为旧兼容，默认使用 `server.js`
