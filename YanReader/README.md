# 砚 · YanReader

一个全新的小说下载 / 导出应用。

| 项 | 值 |
| --- | --- |
| 应用名 | 砚 |
| 包名 / applicationId | `ink.yan.reader` |
| 命名空间 | `ink.yan.reader` |
| 技术栈 | Kotlin 2.1.21 + Jetpack Compose Material 3（BOM 2026.06.01 / Compose 1.11.4） |
| minSdk / targetSdk | 26 / 36 |
| 版本 | 0.1.0（versionCode 1） |

包名与应用名与「观隅」「弦」均不相同，是独立应用。

---

## 一、它解决什么问题

一句话：**把「找书 → 抓章节 → 导出成能看的书」这条链路做成可插拔的，数据源由用户自己配。**

应用**不内置任何内容源，也不硬编码任何接口地址**。节点（数据源）是运行时由用户在「数据源」页添加、启用、测速、排序的，地址形如 `http://host:port`。这样节点挂了改地址就行，不用改代码、不用发版。

---

## 二、构建

```bash
# 调试包
./gradlew :app:assembleDebug

# 单元测试（纯 JVM，不需要模拟器）
./gradlew :app:testDebugUnitTest

# 发布包
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/`。

**构建前置**：JDK 17 或更高（实测 JDK 20 通过）、Android SDK（compileSdk 36 + build-tools 36.0.0）、Gradle 8.13（wrapper 已配置）。

首次导入 Android Studio 时会自动生成 `local.properties` 指向你的 SDK；命令行构建若报 SDK 找不到，手动写一行 `sdk.dir=/你的/SDK/路径` 即可（该文件已被 `.gitignore` 忽略）。

### 关于仓库与网络

`settings.gradle.kts` 里腾讯公共镜像放在最前做加速，后面依次保留 `google()`、`mavenCentral()`、`gradlePluginPortal()` 兜底。

> 这里有个坑值得记一下：**不要**写成 `google { url = uri("镜像地址") }`。语法上合法（Gradle 5.3+ 的 `google(Action)` 重载），但它会把 Google Maven 的地址整体替换掉，而公共镜像通常只代理 Maven Central，不保证含 AGP / AndroidX / Compose 工件，一旦缺失插件解析直接失败。用 `maven { url = ... }` 做加速、保留官方仓库兜底才稳。

如果 `services.gradle.org` 拉不到 wrapper 分发包，把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成 `https://mirrors.cloud.tencent.com/gradle/gradle-8.13-bin.zip`。

---

## 三、文件地图

```
app/src/main/java/ink/yan/reader/
├── MainActivity.kt            Compose 入口，三个页面 + 导航
├── YanApp.kt                  Application，通知渠道
├── data/
│   ├── Models.kt              纯 Kotlin 模型（不依赖 android.*，可在 JVM 直接测）
│   ├── NodeTester.kt          节点测速 + NodeRepository 内存态管理
│   ├── NodeCodec.kt           节点列表序列化（纯 Kotlin）
│   ├── DownloadEngine.kt      并发下载引擎  ← 核心
│   └── export/
│       ├── TxtWriter.kt       流式 TXT 写出
│       └── EpubWriter.kt      流式 EPUB 3.0 写出（零依赖，仅 java.util.zip）
├── store/
│   └── NodeStore.kt           DataStore 落盘
├── ui/
│   ├── Glass.kt               液态玻璃材质
│   ├── NodeScreen.kt          数据源管理
│   ├── SearchScreen.kt        搜索 / 下载
│   ├── SettingsScreen.kt      设置
│   └── theme/Theme.kt         配色
└── vm/
    └── MainViewModel.kt       引擎与 UI 的桥
```

---

## 四、几个关键取舍

### 1. 并发下载，且结果与目录同序

`DownloadEngine` 用 `Semaphore` 限流（默认 5，实测 4~6 是吞吐与稳定性的平衡点），结果**按章节下标写回定长数组**。并发的完成顺序是乱的，直接 `append` 到 `List` 会导出一本乱序的书。

连续失败 10 次即中止，避免整本书空转。失败的章节写占位文本而不是丢弃，这样导出的书里能直接看到「哪一章没抓到」。

### 2. 缓存回调由引擎加锁

抓取是并发的，而调用方传进来的缓存容器**很可能是普通的 `HashMap`**。开发时实测 40 章只缓存了 39 章——静默丢数据，很难查。引擎内部统一用 `Mutex` 串行化缓存读写，调用方即使用非线程安全容器也不会丢条。代价可忽略，但省掉一整类隐蔽 bug。

### 3. 导出只保留 TXT 和 EPUB

`ExportFormat` 只有两个枚举值，这是刻意的。

不做 NVB 之类私有格式：没有第三方阅读器支持，用户导出的书只能在自家应用里看，等于把书锁死。TXT 通用、EPUB 是行业标准，这两个够了。

### 4. 全部流式写出

`TxtWriter` / `EpubWriter` 都只 `flush()` 不 `close()`——`OutputStream` 的生命周期归调用方（MediaStore 的 Uri 或 `FileOutputStream`）管。一部 3000 章的小说正文十几 MB，先拼成一个 String 再写，低端机上很容易 OOM。

`EpubWriter` 零第三方依赖，只用 `java.util.zip`。注意 `mimetype` 必须是 ZIP 的**第一个条目**且为 **STORED 未压缩**；Java 的 `ZipEntry` 在 STORED 模式下必须手动设置 `size`、`compressedSize`、`crc` 三者，少一个就抛 `ZipException`。

### 5. 液态玻璃是「近似」，不是真折射

Apple 的 Liquid Glass 依赖 Metal 层的实时折射与镜面高光，Compose 拿不到那层能力。`Glass.kt` 用四层渐变叠绘逼近：填充渐变（模拟厚度不均）→ 渐变描边（边缘折射）→ 顶部高亮弧（光源反射）→ 底部反光（避免沉进背景）。

全程只用 `drawWithCache`，不创建额外 Layer，不触发逐帧重采样，滚动时 60fps 没问题。

Compose 没有系统级 backdrop blur 原语。**不建议**为此叠加 `Modifier.blur()`：它会逐帧 GPU 重采样，列表里大量用会明显掉帧。真要背景模糊，只建议用在静态浮层（弹窗、详情页头部）上。

### 6. 节点持久化只存一行字符串

`NodeStore` 用 DataStore 存一个字符串，不上 Room——节点数量是个位数到几十，为一张表引一整套 ORM 不划算。

`NodeCodec` 用单元分隔符 `US(0x1F)` 切字段、换行切记录，字段值全部过一遍 URL 编解码，所以名称里带换行、制表符、emoji、中文都不会把格式撑坏。字段数不对、id 或 baseUrl 为空、布尔值非法的行一律静默跳过，保证旧版本数据和手工编辑后的容错。

### 7. 节点只在启动时读一次快照

`MainViewModel` 用 `store.nodes.first()` 一次性读取，而不是 `collect` 整条 flow。

原因是竞态：若持续 collect，落盘回灌的旧快照会覆盖两次点击之间的内存改动——快速连点「添加」会丢节点。当前没有外部写入方，一次性读取更安全。

---

## 五、已知未完成项

`MainViewModel.kt` 里有**两处刻意保留的占位**：

| 位置 | 现状 | 为什么留 |
| --- | --- | --- |
| `search()` | 返回空列表 | 应用不内置内容源，搜索接口取决于用户配置哪个节点、节点提供什么协议，没有可以写死的默认实现 |
| `fetchChapter()` | 返回空正文 | 同上 |

这两处是**设计选择而非疏漏**。应用定位就是「壳 + 管线」，节点协议由你填。接的时候改这两个函数即可，其余部分（并发、缓存、续传、导出、UI）都与之解耦。

其余功能都是完整可用的：节点增删 / 启用 / 测速 / 排序 / 持久化、并发下载、断点续传、TXT 与 EPUB 导出到 `Download/YanReader/`。

---

## 六、测试

```bash
./gradlew :app:testDebugUnitTest
```

`app/src/test/java/ink/yan/reader/CoreLogicTest.kt` 是纯 JVM 单元测试，不依赖 Robolectric，覆盖三块最容易出错且肉眼难发现的地方：

1. **节点序列化** —— 往返一致、特殊字符（换行 / 分隔符本身 / emoji / URL 元字符）存活、脏数据容错
2. **并发下载** —— 40 章全部成功、顺序与目录一致、缓存无丢写、第二轮零网络请求（续传生效）
3. **EPUB 结构** —— `mimetype` 为首个未压缩条目、`container.xml` 与 OPF 存在、全部 XML（含章节 xhtml）良构、`<>&"'` 已正确转义

`DownloadEngine` 的测试**故意传入普通 `HashMap`** 作为缓存容器，就是用来守住上面第 2 节那个并发丢写 bug 的。

---

## 七、依赖版本说明

有一个坑值得单独写下来：**Compose BOM `2026.08.00` 不能配 AGP 8.13.2**。

它管理的 Compose 是 1.12.0，而 1.12.0 要求 `compileSdk >= 37` 且 `AGP >= 9.1.0`，在这个组合下会直接构建失败：

```
Dependency 'androidx.compose.ui:ui:1.12.0' requires Android Gradle plugin 9.1.0 or higher.
Dependency 'androidx.compose.ui:ui:1.12.0' requires ... compile against version 37 or later
```

各 BOM 版本与 Compose 的对应关系（实测拉取 BOM 的 pom 得到）：

| BOM | Compose | 结论 |
| --- | --- | --- |
| 2025.12.01 | 1.10.0 | 可用 |
| 2026.02.01 | 1.10.4 | 可用 |
| 2026.04.01 | 1.11.0 | 可用 |
| 2026.06.01 | 1.11.4 | **本项目采用** |
| 2026.08.00 | 1.12.0 | 需 AGP 9.1+ / compileSdk 37 |

本项目锁定 `2026.06.01`。如果后续升级 AGP 到 9.1+ 并把 `compileSdk` 提到 37，再升 BOM。

`buildToolsVersion` 也做了显式锁定（36.0.0）。不写的话 AGP 会用内置的默认版本（当前是 35.0.0），在只装了 36.x 的机器上会报 `Failed to find Build Tools revision 35.0.0`。

---

## 八、验证状态

**已完整构建通过**，不是"应该能编过"：

```
./gradlew :app:assembleDebug   → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest → 6 tests, 0 failures
```

产物校验（用 `aapt dump badging` 实测 APK）：

| 项 | 实测值 |
| --- | --- |
| package | `ink.yan.reader` |
| application-label | 砚 |
| versionCode / versionName | 1 / 0.1.0 |
| minSdk / targetSdk | 26 / 36 |
| launchable-activity | `ink.yan.reader.MainActivity` |
| APK 体积 | 约 20 MB（debug） |

单元测试 6 个用例全部通过，覆盖节点序列化、并发下载、EPUB 结构三块（详见第六节）。

**未经真机验证的部分**：MediaStore 导出、DataStore 落盘、FileProvider 这些必须在设备上跑才知道对不对。建议第一次安装后先跑一遍完整流程：加节点 → 测速 → 下载 → 到 `Download/YanReader/` 下确认文件能打开。

> release 包需要你补签名配置，项目里没有写 `signingConfigs`。
