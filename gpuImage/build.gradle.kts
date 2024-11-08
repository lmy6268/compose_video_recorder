plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    compileSdk = 34
    namespace = "jp.co.cyberagent.android.gpuimage"
    defaultConfig {
        minSdk = 27
        lint.targetSdk = 34
        ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

        externalNativeBuild {
            cmake {
                cppFlags.add("")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = File("src/main/cpp/CMakeLists.txt")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
    implementation(projects.opencv)
}

//val bintrayRepo = "maven"
//val bintrayName = "gpuimage"
//val bintrayUserOrg = "cats-oss"
//val publishedGroupId = "jp.co.cyberagent.android"
//val libraryName = "gpuimage"
//val artifact = "gpuimage"
//val libraryDescription = "Image filters for Android with OpenGL (based on GPUImage for iOS)"
//val siteUrl = "https://github.com/cats-oss/android-gpuimage"
//val gitUrl = "https://github.com/cats-oss/android-gpuimage.git"
//val issueUrl = "https://github.com/cats-oss/android-gpuimage/issues"
//val developerId = "cats"
//val developerName = "CATS"
//val developerEmail = "dadadada.chop@gmail.com"
//val licenseName = "The Apache Software License, Version 2.0"
//val licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0.txt"
//val allLicenses = listOf("Apache-2.0")
//
//// Apply external Gradle script
//apply(from = "https://gist.githubusercontent.com/wasabeef/2f2ae8d97b429e7d967128125dc47854/raw/maven-central-v1.gradle")