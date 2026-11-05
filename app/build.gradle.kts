plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ledger.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ledger.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    // 签名 keystore 只保存在构建服务器上，不入公开仓库；克隆的源码没有它时 release 不签名
    val releaseKeystore = file("release.keystore")
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "ledger123"
            keyAlias = "ledger"
            keyPassword = "ledger123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore.exists()) signingConfig = signingConfigs.getByName("release")
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
    }
    testOptions {
        // pdfbox-android 在 JVM 单测中会调用 android.util.Log 等桩方法
        unitTests.isReturnDefaultValues = true
    }
    sourceSets["test"].resources.srcDir(layout.buildDirectory.dir("pdfbox-test-res"))
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        // BouncyCastle（pdfbox-android 传递依赖）的后量子算法参数表，PDF 文本提取用不到
        resources.excludes += "org/bouncycastle/pqc/**"
    }
}

// 单测：把 pdfbox-android AAR 里的 assets（glyphlist/字体等）放到测试 classpath，
// 未调用 PDFBoxResourceLoader.init 时它会回退到 classpath 加载，从而在 JVM 上跑真实提取链路
val pdfboxAar: Configuration by configurations.creating { isTransitive = false }
val extractPdfboxTestAssets by tasks.registering(Copy::class) {
    from({ pdfboxAar.map { zipTree(it) } }) {
        include("assets/**")
        eachFile { path = path.removePrefix("assets/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("pdfbox-test-res"))
}
tasks.matching { it.name.startsWith("process") && it.name.endsWith("UnitTestJavaRes") }
    .configureEach { dependsOn(extractPdfboxTestAssets) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // PDF 账单文本提取（Apache PDFBox 的 Android 移植，Apache 2.0）
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    // 仅单元测试：android.jar 里的 org.json 是桩实现，测试 UpdateChecker.parseRelease 需真实实现（App 运行时用系统自带 org.json）
    testImplementation("org.json:json:20240303")
    // 仅单元测试：用桌面版 PDFBox 生成/提取真实 PDF，验证 PDF 解析链路（不进 APK）
    testImplementation("org.apache.pdfbox:pdfbox:2.0.27")
    pdfboxAar("com.tom-roush:pdfbox-android:2.0.27.0@aar")
}
