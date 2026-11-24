plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cc.csxcl.xiaobuhabridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.csxcl.xiaobuhabridge"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    packaging {
        resources {
            excludes += "**/*.kotlin_builtins"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.activity:activity-compose:1.13.0")
    val couiVersion = providers.gradleProperty("couiVersion").get()
    implementation("io.github.suqi8.coui.kmp:coui-ui:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-preference:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-nav:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-squircle:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-icons:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-blur:$couiVersion")

    implementation("org.luckypray:dexkit:2.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
