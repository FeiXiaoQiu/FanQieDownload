// 仓库顺序按环境切换：
//
// · 本地（无 CI 环境变量，通常在中国大陆）：腾讯镜像放最前加速，官方源兜底。
// · CI（GitHub Actions，runner 在境外，CI=true）：跳过镜像直接用官方源。
//   境外访问腾讯镜像会超时，反而拖垮首次构建。
//
// 注意：判断必须内联在各个块内部，不能提成外面的 val ——
// pluginManagement { } 在 Kotlin DSL 里是独立的脚本作用域，
// 访问不到脚本顶层的局部变量，会直接编译报错
// "Unresolved reference: useMirror"，导致所有 Gradle 任务失败。
//
// 另外：不要写成 google { url = uri(镜像) } —— 那会把 Google Maven 地址整体替换掉，
// 而公共镜像通常只代理 Maven Central，不保证含 AGP / AndroidX / Compose 等工件，
// 缺失时插件解析会直接失败。用 maven { url = ... } 与官方源并列才安全。
pluginManagement {
    repositories {
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "YanReader"
include(":app")
