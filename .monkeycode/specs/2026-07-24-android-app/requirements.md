# Requirements Document

## Introduction

为番茄小说下载器提供 **Android 原生客户端（Kotlin）**，布局对齐现有 Web 黑底圆角白卡风格。应用在设备本地完成搜索、简介预览、TXT 下载；支持在设置面板中增删改「番茄节点 API」列表，以及配置外接「一言」HTTP API。源码放在本仓库 `android/` 目录，通过 GitHub Actions 产出 APK 并挂到 Release。

首版范围：**搜索 + 简介 + 下载 + API/一言配置**。阅读页、批量多选下载队列可列为二期。

## Glossary

- **System / App**：番茄小说下载器 Android 应用
- **番茄节点 API**：提供与现有第三方节点兼容接口的 HTTP 基址，路径约定为：
  - `GET {base}/search?query={q}&offset={n}`
  - `GET {base}/info?book_id={id}`
  - `GET {base}/catalog?book_id={id}`
  - `GET {base}/content?item_id={id}`
- **节点条目**：用户可增删的一条基址配置（名称、URL、启用开关、顺序）
- **一言 API**：返回一句文案的外接 HTTP 接口（推荐 [Hitokoto](https://v1.hitokoto.cn/) 类 JSON 或纯文本）
- **Web 布局**：黑底背景 + 圆角白卡片主区 + 搜索栏工具页风格
- **单本下载**：按书籍 ID 拉取目录与章节正文，合并为本地 `.txt` 文件

## Requirements

### Requirement 1 — 主页搜索与结果列表

**User Story:** AS 读者, I want 在手机上按书名搜索番茄小说, so that 我能快速找到目标书并进入简介或下载

#### Acceptance Criteria

1. WHEN 用户在搜索框输入非空书名并触发搜索（按钮或回车）, THE App SHALL 使用当前启用的番茄节点 API 发起搜索并展示结果列表
2. WHEN 搜索返回多条结果, THE App SHALL 展示书名、作者/状态等元信息、可选封面与简短简介摘要
3. WHEN 列表可分页且节点返回 has_more 或等价信息, THE App SHALL 提供「加载更多」并追加结果
4. WHEN 搜索无结果, THE App SHALL 在主区展示明确的空状态文案
5. WHEN 搜索失败（网络错误、全部节点失败、解析失败）, THE App SHALL 展示可读错误信息并允许用户重试
6. WHILE 主页处于空搜索态, THE App SHALL 提供与 Web 一致的示例书名提示（如「抽象职校生存手册」），用户可一键填入并搜索

### Requirement 2 — 书籍简介预览

**User Story:** AS 读者, I want 点击搜索结果查看简介, so that 我确认是否下载该书

#### Acceptance Criteria

1. WHEN 用户点击某条搜索结果主区域, THE App SHALL 打开简介界面（底部弹层或新页面）并请求书籍详情
2. WHEN 详情加载成功, THE App SHALL 展示书名、作者、字数/状态等元信息及完整简介正文
3. WHEN 简介正文存在多段, THE App SHALL 按段展示并采用首行缩进两字的阅读样式
4. WHEN 简介界面打开, THE App SHALL 提供「下载」主操作入口
5. IF 详情请求失败, THE App SHALL 展示错误信息并允许关闭或重试

### Requirement 3 — 单本 TXT 下载

**User Story:** AS 读者, I want 把整本或指定章节范围下载为 TXT, so that 我能离线阅读

#### Acceptance Criteria

1. WHEN 用户从简介或结果操作发起单本下载, THE App SHALL 展示下载选项：起始章、结束章（空或 0 表示整本）、是否断点续传
2. WHEN 用户确认下载, THE App SHALL 顺序获取目录与各章正文，合并为单个 UTF-8 `.txt` 文件，优先写入系统公共 Download 目录
3. WHILE 下载进行中, THE App SHALL 展示进度（当前章节序号 / 总章数或百分比）并允许取消
4. WHEN 下载成功, THE App SHALL 提示保存路径，并在系统允许时提供「打开文件」或「分享」入口
5. IF 某一章请求失败, THE App SHALL 在有限次重试后跳过该章并继续后续章节，并在结果中汇总失败章号；连续失败达到阈值时可中止整次任务
6. WHEN 用户选择断点续传且本地存在该书章节缓存, THE App SHALL 优先使用缓存章节以减少重复请求

### Requirement 4 — 番茄节点 API 配置面板

**User Story:** AS 用户, I want 在设置里增删改番茄 API 服务器地址, so that 节点失效时我能自己换源

#### Acceptance Criteria

1. THE App SHALL 内置与 Web 端一致的默认节点列表（至少包含当前 `browser-client.js` / `nodes.alive.json` 中的固定主机）
2. WHEN 用户打开设置中的「番茄 API」面板, THE App SHALL 列出全部节点：显示名称、基址 URL、启用状态、顺序
3. WHEN 用户新增节点, THE App SHALL 要求填写可解析的 HTTP(S) 基址（无尾斜杠或自动规范化），并默认启用
4. WHEN 用户编辑或删除自定义节点, THE App SHALL 持久化变更；内置节点允许禁用，删除策略在 design 中约定（建议：内置可重置、自定义可删）
5. WHEN 用户点击「测活」或保存后可选测活, THE App SHALL 对目标节点请求探活用 `content?item_id=` 固定样例 ID（与 Web `PROBE_ITEM` 一致）并展示延迟或失败原因
6. WHILE 存在多个启用节点, THE App SHALL 在请求时按顺序或延迟优先策略尝试，某一节点失败后切换下一节点
7. WHEN 用户执行「恢复默认节点」, THE App SHALL 将番茄节点列表恢复为内置默认集

### Requirement 5 — 一言外接 API

**User Story:** AS 用户, I want 用外接一言 API 而不是内置大词库, so that 应用体积更小且文案可自选源

#### Acceptance Criteria

1. THE App SHALL 默认使用一言 HTTP API `https://v1.hitokoto.cn/`，并允许用户改为其它等价接口
2. WHEN 主页需要展示一言, THE App SHALL 请求当前配置的一言 URL，将返回内容展示在不遮挡主搜索卡片核心操作的区域（右上角小卡片或顶栏次要区域，具体布局在 design 中与 Web 对齐并避免叠住标题/搜索框）
3. WHEN 一言 API 返回 JSON, THE App SHALL 按可配置字段路径或内置常见字段（如 `hitokoto` + `from` / `from_who`）拼成展示文案；署名格式优先：`正文——来源` 或 `正文——来源·作者`
4. WHEN 一言 API 返回纯文本, THE App SHALL 直接展示该文本
5. IF 一言请求失败, THE App SHALL 静默使用少量内置兜底短句（条数 ≤ 30），保证界面仍可展示一条
6. WHEN 用户在设置中修改一言 URL 或解析模式, THE App SHALL 持久化配置并在下次刷新一言时生效

### Requirement 6 — 视觉与导航

**User Story:** AS 用户, I want 界面风格接近现有网站, so that 两端体验一致

#### Acceptance Criteria

1. THE App SHALL 使用深色背景 + 圆角浅色主卡片作为主视觉
2. THE App SHALL 提供主导航或入口：搜索主页、设置（番茄 API + 一言）
3. THE App SHALL 避免主搜索区放置多余口号与大量 emoji
4. WHEN 系统为深色/浅色模式, THE App SHALL 以产品固定黑底白卡风格为主（首版不强制跟随系统 Material You 全动态取色）

### Requirement 7 — 构建、安装与仓库集成

**User Story:** AS 维护者, I want 源码在本仓库并用 GitHub 出 APK, so that 用户能直接下载安装

#### Acceptance Criteria

1. THE App 源码 SHALL 位于仓库根目录 `android/` 下，可被 Android Studio 打开
2. WHEN 推送 tag 或手动触发 Release workflow, THE CI SHALL 构建 debug 或 release APK 并上传为 Release 资产
3. THE README（或 `android/README.md`）SHALL 说明：环境要求、本地编译命令、默认权限、配置项含义
4. THE App SHALL 声明联网权限；下载文件写入应用目录或公共 Downloads 时遵循目标 Android 版本的存储规范

### Requirement 8 — 非功能与约束

#### Acceptance Criteria

1. THE App SHALL 仅在用户主动操作时发起搜索与下载相关网络请求（一言可在进入主页时自动请求一次）
2. THE App SHALL 在 UI 与文档中提示：仅供个人学习研究，遵守版权与平台条款
3. THE App 首版范围 EXCLUDES：完整阅读页、搜索结果批量多选队列、Vercel 账号体系、云同步配置
4. IF 用户禁用或删除后导致零个启用节点, THE App SHALL 阻断搜索与下载请求，并提示用户至少启用一个番茄节点（不自动静默恢复；用户可点「恢复默认节点」）

## Resolved Decisions

| 项 | 结论 |
|----|------|
| TXT 保存位置 | 系统公共 Download 目录 |
| 零启用节点 | 阻断并要求至少启用一个；可用「恢复默认」 |
| 一言默认 | `https://v1.hitokoto.cn/` |
| 技术栈 | Kotlin 原生 |
| 交付 | 仓库 `android/` + GitHub Release APK |
| 首版范围 | 搜索 + 简介 + 下载 + API/一言配置 |

## Out of Scope (v1)

- 应用内章节阅读器（Web `reader.html` 对齐）
- 搜索列表批量勾选下载多本
- iOS / 鸿蒙多端
- 账号登录、付费、广告 SDK
- 将整份 `Speech/Speech.txt` 打进 APK

