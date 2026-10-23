plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.rm014s"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mandix.remote"
        minSdk = 21
        targetSdk = 34
        versionCode = 12
        versionName = "4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Cliente ADB (protocolo + autenticación RSA) para manejar el Fire TV por Wi-Fi
    implementation("dev.mobile:dadb:1.2.9") {
        // su POM publica JUnit como dependencia de ejecución: no va en la app
        exclude(group = "org.junit.platform")
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.opentest4j")
        exclude(group = "org.apiguardian")
    }
    testImplementation("junit:junit:4.13.2")
}
