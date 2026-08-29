# 砚 · YanReader

一个全新的小说下载 / 导出应用。

| 项 | 值 |
| --- | --- |
| 应用名 | 砚 |
| 包名 / applicationId | `ink.yan.reader` |
| 命名空间 | `ink.yan.reader` |
| 技术栈 | Kotlin 2.1.21 + Jetpack Compose Material 3（BOM 2026.06.01 / Compose 1.11.4） |
| minSdk / targetSdk | 26 / 36 |
| 版本 | 0.2.0（versionCode 2） |
| 签名 | 独立密钥 `CN=YanReader`（与观隅 `com.feixiaoqiu.fanqiedl` 完全不同，两者可共存安装） |
| 发布 | 推送 `yanreader-v*` tag 后由 GitHub Actions 自动构建并发布 |

包名、应用名、签名与「观隅」「弦」均不相同，是独立应用。

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

### 签名

密钥**不进仓库**。项目根目录放 `keystore.properties` 即可启用真实签名：

```properties
storeFile=yanreader.jks
storePassword=***
keyAlias=yanreader
keyPassword=***
```

四个键齐全且文件存在时走 `signingConfigs.release`，否则自动回退 debug 签名——所以没有密钥也能 `assembleRelease` 跑通，只是产物不能用。

CI 上这个文件由工作流从 GitHub Secrets 生成（`YANREADER_KEYSTORE_BASE64` / `YANREADER_STORE_PASSWORD` / `YANREADER_KEY_ALIAS` / `YANREADER_KEY_PASSWORD`）。

> 私钥一旦提交到公开仓库就永远无法撤销，任何人都能冒名签发同名应用。观隅把 `android/keystore` 直接放进仓库的做法不建议延续到新项目。`.gitignore` 已排除 `keystore.properties`、`*.jks`、`*.keystore`、`*.p12`。

### 自动发布

推送 `yanreader-v*` 形式的 tag 即触发 `.github/workflows/yanreader-release.yml`：单元测试 → release 构建 → 签名校验 → 打包为 `yanreader-{version}.apk` → 发布 Release。

tag 前缀特意避开观隅的 `v*`，两个工作流互不触发。

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
│   ├── Appearance.kt          风格预设 / 玻璃参数 / 背景缩放（纯 Kotlin）
│   ├── BackgroundSource.kt    背景图源模型 + 预置清单
│   ├── HitokotoSource.kt      一言源模型 + 预置清单 + 兜底文案
│   ├── JsonValue.kt           极简 JSON 值模型（让解析逻辑可脱离 org.json 单测）
│   ├── JsonAdapter.kt         org.json ↔ JsonVal 转换（**唯一** import org.json 处）
│   ├── BackgroundResolver.kt  背景地址解析：路径求值 + 全树探测 + 相对路径补全 ← 核心
│   ├── BackgroundFetcher.kt   背景网络层（含 cache-bust）
│   ├── HitokotoParser.kt      一言响应解析：多字段回退 + 兜底取句
│   ├── HitokotoClient.kt      一言网络层
│   └── export/
│       ├── TxtWriter.kt       流式 TXT 写出
│       └── EpubWriter.kt      流式 EPUB 3.0 写出（零依赖，仅 java.util.zip）
├── store/
│   ├── NodeStore.kt           DataStore 落盘（节点）
│   └── AppearanceStore.kt     DataStore 落盘（外观 / 背景 / 一言 / 导出格式）
├── ui/
│   ├── Glass.kt               液态玻璃材质 + LocalGlassStyle
│   ├── Backdrop.kt            全屏背景渲染（FIT 用同图模糊填白）
│   ├── NodeScreen.kt          数据源管理
│   ├── SearchScreen.kt        搜索 / 下载 / 首页一言
│   ├── SettingsScreen.kt      设置（组装各分区）
│   ├── settings/
│   │   ├── SettingsParts.kt   分区卡片 / 单选行 / 开关行 / 滑杆行
│   │   ├── LookSection.kt     外观风格
│   │   ├── BackgroundSection.kt  背景源 + 显示参数 + 自定义接口
│   │   └── HitokotoSection.kt 一言开关 + 源选择 + 自定义接口
│   └── theme/Theme.kt         配色（随风格预设切换）+ 下发玻璃参数
└── vm/
    └── MainViewModel.kt       引擎、外观、背景、一言的中枢
```

> `JsonValue.kt` 存在的理由：org.json 在 Android 上是平台内置，JVM 单测里却是
> android.jar 的 stub，一调用就抛 "Method ... not mocked"。把解析算法面向
> [JsonVal] 编写、把 org.json 隔离到 `JsonAdapter.kt` 一个文件，背景与一言的
> 解析逻辑才能写真正的单元测试（21 个用例）。

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

## 五、外观与背景系统

### 1. 风格包 + 微调

四套预设（砚青 / 黛蓝 / 朱砂 / 墨白），每套 =「底色 + 主色 + 一组玻璃参数」。选中后还能单独调浓度与圆角，微调字段用可空的 `null` 表示「跟随预设」，切换风格包时清空。

玻璃参数必须跟着配色一起变，不能各自独立 —— 否则会出现「换成浅色但玻璃本色还是白的」这种割裂：白描边画在白底上等于没画。所以墨白预设的 `glassTint` 是深灰蓝而不是白色。

### 2. 玻璃参数靠 CompositionLocal 下发

`LocalGlassStyle` 由 `YanTheme` 提供，业务组件统一写 `Modifier.glass()`。加新组件时不必逐处传参，也不会漏掉某个组件没跟着换风格。

`glass(cornerDelta)` 传的是**相对基准的偏移**而不是绝对值：卡片 -4、输入框 -2。这样用户调圆角时整体等比变化，不会把所有层级差异拉平。

### 3. 背景接口至少三种形态，解析器都得吃

| 形态 | 例子 | 处理 |
| --- | --- | --- |
| 直链出图 | `t.alcy.cc/ycy` | 302 直接跳 webp，交给 Coil 跟随重定向 |
| JSON + 绝对路径 | `acg.yaohud.cn` | 解析 JSON 取图片地址 |
| JSON + 相对路径 | 必应每日一图 | 取出的 `/th?id=...` 必须补 origin 才有效 |

观隅的实现是硬编码 `data[].urlsList[].url` 一家的结构，换个接口就失效 —— 而「动态选择 + 自定义」恰恰要求支持任意接口。这里改成三层策略：

- **显式路径优先**：点分字段，遇数组自动展开。`images.url`、`data.urlsList.url`（兼容观隅老格式）、`data[0].url` 都认
- **全树探测兜底**：路径取不到时不报错，退回遍历整棵树找像图片 URL 的字符串
- **相对路径补全**：图片判定也刻意不要求扩展名在结尾 —— 必应的地址是 `.jpg&rf=...&pid=hp`，只认结尾会漏掉

### 4. 两个不写就会踩的坑

**缓存穿透**：同一个地址不加时间戳，Coil 命中缓存，用户点「换一张」界面毫无反应，很容易被当成功能没做。所以 `refreshBackground(bust = true)` 会附加 `_t=` 参数 —— 且必须判断原地址是否已有 query，直接拼 `?_t=` 会破坏原有参数。

**本地图片不能直接存 URI**：相册返回的 `content://` 是一次性授权，进程重启后就没权限了，背景会静默变回纯色，而且极难排查。必须复制到私有目录再存路径。

### 5. 滑杆只在松手时落盘

`SliderRow` 把拖动中的高频回调（`onValue`，只改内存）与松手回调（`onCommit`，写 DataStore）分开 —— 一次拖动会产生几十次值变化，每次都写磁盘纯属浪费。

同理，外观设置在 `MainViewModel` 里只读一次快照而不 `collect` 整条 flow：否则落盘回灌的旧值会把用户正在拖的数值拽回去，表现为「滑杆自己往回跳」。节点列表用的是同一个策略，理由见第四节第 7 条。

---

## 六、已知未完成项

`MainViewModel.kt` 里有**两处刻意保留的占位**：

| 位置 | 现状 | 为什么留 |
| --- | --- | --- |
| `search()` | 返回空列表 | 应用不内置内容源，搜索接口取决于用户配置哪个节点、节点提供什么协议，没有可以写死的默认实现 |
| `fetchChapter()` | 返回空正文 | 同上 |

这两处是**设计选择而非疏漏**。应用定位就是「壳 + 管线」，节点协议由你填。接的时候改这两个函数即可，其余部分（并发、缓存、续传、导出、UI）都与之解耦。

其余功能都是完整可用的：节点增删 / 启用 / 测速 / 排序 / 持久化、并发下载、断点续传、TXT 与 EPUB 导出到 `Download/YanReader/`。

---

## 七、测试

```bash
./gradlew :app:testDebugUnitTest
```

`app/src/test/java/ink/yan/reader/CoreLogicTest.kt` 是纯 JVM 单元测试，不依赖 Robolectric，覆盖三块最容易出错且肉眼难发现的地方：

1. **节点序列化** —— 往返一致、特殊字符（换行 / 分隔符本身 / emoji / URL 元字符）存活、脏数据容错
2. **并发下载** —— 40 章全部成功、顺序与目录一致、缓存无丢写、第二轮零网络请求（续传生效）
3. **EPUB 结构** —— `mimetype` 为首个未压缩条目、`container.xml` 与 OPF 存在、全部 XML（含章节 xhtml）良构、`<>&"'` 已正确转义

`DownloadEngine` 的测试**故意传入普通 `HashMap`** 作为缓存容器，就是用来守住上面第 2 节那个并发丢写 bug 的。

---

## 八、依赖版本说明

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

## 九、验证状态

**已完整构建通过**，不是"应该能编过"：

```
./gradlew :app:testDebugUnitTest → BUILD SUCCESSFUL（27 tests, 0 failures）
./gradlew :app:assembleRelease   → BUILD SUCCESSFUL（含 R8 混淆 + 真实签名）
```

产物校验（`aapt dump badging` + `apksigner verify` 实测 release APK）：

| 项 | 实测值 |
| --- | --- |
| package | `ink.yan.reader` |
| application-label | 砚 |
| versionCode / versionName | 2 / 0.2.0 |
| minSdk / targetSdk | 26 / 36 |
| launchable-activity | `ink.yan.reader.MainActivity` |
| 签名 SHA1 | `1A:0B:AE:E8:6F:81:D1:C1:7E:B5:45:E9:DF:97:2B:32:85:BB:56:2B` |
| 签名 SHA256 | `C8:D0:8A:2C:5E:A6:08:48:CF:68:7F:F4:94:2E:9A:56:CC:8B:3A:24:9A:53:18:02:6D:0B:4C:9F:9A:34:FA:5C` |
| APK 体积 | 1.8 MB（release，R8 后）/ 约 20 MB（debug） |

签名与 0.1.0 完全一致，可以直接覆盖安装升级。

单元测试 27 个用例全部通过：

- **6 个**（`CoreLogicTest`）—— 节点序列化、并发下载、EPUB 结构
- **21 个**（`AppearanceLogicTest`）—— 背景地址解析（必应相对路径、观隅老格式兼容、方括号下标、路径失效回退探测、非图片过滤、协议相对地址）、cache-bust 的有无 query 分支、一言解析（标准字段 / 备选字段 / data 包裹 / 纯文本 / 字段名未知时兜底）、外观预设与微调覆盖

CI 上一次运行（run 33225938215）17 步全绿，Release 附件 `yanreader-0.1.0.apk`（1,517,666 字节）已发布。

**未经真机验证的部分**：MediaStore 导出、DataStore 落盘、FileProvider 这些必须在设备上跑才知道对不对。建议第一次安装后先跑一遍完整流程：加节点 → 测速 → 下载 → 到 `Download/YanReader/` 下确认文件能打开。

---

## 十、CI 排查记录

构建在 CI 上连续挂了六次，两个根因都值得记下来，因为本地 `gradlew` 与 GitHub runner 的行为不一致：

**1. `secrets` 上下文不能用在 step 的 `if` 里**

```yaml
# 错：工作流文件解析失败，运行 0 秒即失败
if: ${{ secrets.YANREADER_KEYSTORE_BASE64 != '' }}
# 对：先在 job 级 env 里算好，step 用 env.* 判断
env:
  HAS_SIGNING_KEY: ${{ secrets.YANREADER_KEYSTORE_BASE64 != '' }}
if: env.HAS_SIGNING_KEY == 'true'
```

**2. `build.gradle.kts` 不自动导入 `java.util.Properties`**

Kotlin DSL 脚本的默认导入只有 `kotlin.*`、`org.gradle.*` 等几组，没有 `java.util.*`。漏掉这行：

```kotlin
import java.util.Properties
```

报错是 `Unresolved reference: Properties` / `Unresolved reference: load`。它的隐蔽之处在于**脚本编译失败会让所有 Gradle 任务一起挂**，包括跟签名毫无关系的单元测试——所以现象是"测试失败"，根因却在签名配置。

**3. Kotlin DSL 的 `pluginManagement { }` 是独立脚本作用域**

访问不到脚本顶层的 `val`，会报 `Unresolved reference: useMirror`。需要判断的条件请在块内内联写。

**4. 日志拿不到怎么办**

Actions 的日志与 artifact 域名（results-receiver.actions.githubusercontent.com、productionresultssa2.blob.core.windows.net、objects.githubusercontent.com）在部分网络环境不可达，`gh run view --log` 与 `gh run download` 都会失败。

临时解法是让 CI 用 `PUT /repos/{repo}/contents/{path}` 把日志提交回仓库再读。本项目的 `ci-diag/` 就是这么来的，已于构建稳定后清除。
