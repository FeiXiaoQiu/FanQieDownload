plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 签名。
 *
 * 密钥刻意不进仓库（与观隅 android/keystore 的做法不同）：
 * 私钥一旦提交到公开仓库，任何人都能冒名签发同名应用，且永远无法撤销。
 * 本项目的 release 密钥只存在于 GitHub Secrets，CI 构建时写入 keystore.properties。
 *
 * 本地没有密钥时回退 debug 签名，保证 ./gradlew assembleRelease 不会直接失败。
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKey: Boolean = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile") != null &&
    rootProject.file(keystoreProps.getProperty("storeFile")).exists()

android {
    namespace = "ink.yan.reader"
    compileSdk = 36
    // 显式锁定：不写这行时 AGP 会用它内置的默认版本（当前是 35.0.0），
    // 在只装了 36.x 的机器上会报 "Failed to find Build Tools revision 35.0.0"。
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ink.yan.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 无密钥时退回 debug 签名，避免 CI/本地直接构建失败
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    // Compose
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 生命周期与协程
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 设置持久化
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // 图片加载（封面 / 背景）
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
