// AGP 9 applies Kotlin itself; the standalone kotlin-android plugin is rejected.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名凭证存放在 local.properties 中：已加入 gitignore，并由 tools/wbuild.sh 同步到
// Windows 镜像中，因此不会进入 git。
// 全新克隆的仓库中没有此文件，这也是下面 release signingConfig 使用条件判断的原因。
val signing = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.mdview"
    // The androidx versions below refuse to be consumed below 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mdview"
        minSdk = 26
        // Held one release back deliberately: nothing here needs the API 37
        // runtime behaviour changes, and 36 is the level this app was tested at.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            signing.getProperty("release.storeFile")?.let {
                storeFile = file(it)
                storePassword = signing.getProperty("release.storePassword")
                keyAlias = signing.getProperty("release.keyAlias")
                keyPassword = signing.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // 条件判断，因此克隆的仓库仍然可以构建——
            // 未签名，和现在一样。如果不加保护直接赋值，会在配置阶段失败，
            // 报错 "storeFile has not been set"。
            if (signing.getProperty("release.storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // For VERSION_NAME, shown in the settings panel.
        buildConfig = true
    }

    bundle {
        language {
            // The in-app language picker can only offer translations that are actually
            // installed. Per-language splits would ship a device set to English nothing
            // but English, and the Chinese option would silently do nothing.
            enableSplit = false
        }
    }

    testOptions {
        // No physical device is assumed: `wbuild.sh pixelApi34DebugAndroidTest`
        // downloads the image, boots a headless emulator and tears it down again.
        managedDevices {
            localDevices {
                create("pixelApi34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                    // AGP 10 flips the default to arm64-v8a, which this image cannot
                    // translate; being explicit keeps the tests running either way.
                    testedAbi = "x86_64"
                }

                // API 34 only ever exercises LocaleManager. Everything sharp about the
                // in-app language -- the attachBaseContext wrapper, the font-scale trap,
                // Locale.setDefault -- lives on the pre-33 path, which needs a device
                // old enough to take it.
                //
                // Needs its image licence accepted once before it will run:
                //   sdkmanager.bat --licenses
                // The API 30 ATD image is 32-bit only, unlike the API 34 one.
                create("pixelApi30") {
                    device = "Pixel 6"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86"
                }
            }
        }

        unitTests {
            // The inline renderer builds AnnotatedString values, which reach into a
            // few android.jar stubs; return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    implementation(platform(libs.androidx.compose.bom))
    // Declared rather than left transitive: AnimatedContent and CubicBezierEasing are
    // direct API use in ui/theme/Motion.kt, not something inherited through material3.
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.yaml.front.matter)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui)
    // MotionTest evaluates the easing curves on the JVM; they live in animation-core.
    testImplementation(libs.androidx.compose.animation)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
