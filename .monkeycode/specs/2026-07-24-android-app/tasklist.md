# 需求实施计划

- [x] 1. 搭建 `android/` Gradle + Compose 工程骨架
  - 创建 settings/build、app 模块、Manifest、主题资源
  - 添加 GitHub Actions 构建 APK workflow
  - 编写 `android/README.md`
  - 主页背景：`https://t.alcy.cc/ycy`

- [x] 2. 配置与数据层
  - [x] 2.1 NodeConfig / DataStore 读写、默认节点、恢复默认
  - [x] 2.2 FanqieNodeClient（search/info/catalog/content/probe）与 JSON 解析
  - [x] 2.3 HitokotoClient 与兜底句
  - [x] 2.4 DownloadRepository（缓存、进度、MediaStore 写 TXT）
  - [ ]* 2.5 解析与 URL 规范化单元测试

- [x] 3. UI 层
  - [x] 3.1 主页搜索 + 结果列表 + 一言角标
  - [x] 3.2 简介弹层 + 下载选项与进度
  - [x] 3.3 设置页：节点 CRUD/测活、一言 URL

- [x] 4. 检查点
  - 源码齐全；本环境无 SDK，以 GitHub Actions `Android APK` 工作流构建为准
