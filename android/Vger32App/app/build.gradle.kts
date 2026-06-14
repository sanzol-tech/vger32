import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "ar.vger32app"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ar.vger32app"
        versionCode = 1
        versionName = "0.0.1"
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += listOf("version")

    productFlavors {
        create("android11") { dimension = "version"; applicationIdSuffix = ".android11"; minSdk = 30 }
        create("android12") { dimension = "version"; applicationIdSuffix = ".android12"; minSdk = 31 }
        create("android13") { dimension = "version"; applicationIdSuffix = ".android13"; minSdk = 32 }
        create("android14") { dimension = "version"; applicationIdSuffix = ".android14"; minSdk = 33 }
        create("android15") { dimension = "version"; applicationIdSuffix = ".android15"; minSdk = 34 }
        create("android16") { dimension = "version"; applicationIdSuffix = ".android16"; minSdk = 35 }
    }

    signingConfigs {
        create("release") {
            storeFile     = file(signingProps.getProperty("storeFile")     ?: "")
            storePassword =      signingProps.getProperty("storePassword") ?: ""
            keyAlias      =      signingProps.getProperty("keyAlias")      ?: ""
            keyPassword   =      signingProps.getProperty("keyPassword")   ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            signingConfig = signingConfigs.getByName("release")
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules-release.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("vger32app-${variant.name}-v${variant.outputs.firstOrNull()?.versionCode?.orNull ?: 1}.apk")
            }
        }
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.runtime)
    implementation(libs.navigation.ui)
    implementation(libs.preference)

    implementation(libs.biometric)
    implementation(libs.security.crypto)

    implementation(libs.okhttp)

    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.zxing.android.embedded)
}