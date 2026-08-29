// 仓库顺序说明：
// 腾讯公共镜像放在最前做加速，后面保留官方 google() / mavenCentral() 兜底。
// 不要写成 google { url = uri(镜像) } —— 那样会把 Google Maven 的地址整体替换掉，
// 而公共镜像通常只代理 Maven Central，不保证包含 AGP / AndroidX / Compose 等工件，
// 一旦缺失会让插件解析直接失败。Gradle 找不到时会依次向后尝试，所以两者并存最稳。
pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
    }
}

rootProject.name = "YanReader"
include(":app")
