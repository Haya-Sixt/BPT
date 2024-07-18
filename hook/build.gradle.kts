import org.jetbrains.kotlin.daemon.nowSeconds

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "il.hs.bpt"
    compileSdk = 34

    defaultConfig {
        applicationId = "il.hs.bpt"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = nowSeconds().toString() //"4.3"
    }

    buildTypes {
        debug {
            isJniDebuggable = true
            isDebuggable = true
            proguardFiles(
                "proguard-rules.pro"
            )

        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        buildConfig = true
//        viewBinding = true
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//    implementation(libs.androidx.constraintlayout)
}