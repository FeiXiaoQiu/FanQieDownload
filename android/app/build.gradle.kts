import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.feixiaoqiu.fanqiedl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.feixiaoqiu.fanqiedl"
        minSdk = 26
        targetSdk = 34
        // 固定签名密钥勿动
        versionCode = 5
        versionName = "1.0.4"
        ndk {
            // 常见手机架构
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("fanqieDebug") {
            storeFile = rootProject.file(keystoreProps.getProperty("debug.storeFile"))
            storePassword = keystoreProps.getProperty("debug.storePassword")
            keyAlias = keystoreProps.getProperty("debug.keyAlias")
            keyPassword = keystoreProps.getProperty("debug.keyPassword")
        }
        create("fanqieRelease") {
            storeFile = rootProject.file(keystoreProps.getProperty("release.storeFile"))
            storePassword = keystoreProps.getProperty("release.storePassword")
            keyAlias = keystoreProps.getProperty("release.keyAlias")
            keyPassword = keystoreProps.getProperty("release.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fanqieRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 本地调试用固定 debug 密钥；对外发布仅 release 产物
            signingConfig = signingConfigs.getByName("fanqieDebug")
        }
    }

    // 按架构拆包 + 全架构 universal
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
