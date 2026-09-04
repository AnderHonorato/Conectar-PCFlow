import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A chave de assinatura fica fora do repositório (keystore.properties está no
// .gitignore). Sem ela, o release cai para a chave de depuração.
val arquivoChaves = rootProject.file("keystore.properties")
val chaves = Properties().apply {
    if (arquivoChaves.exists()) arquivoChaves.inputStream().use { load(it) }
}

android {
    namespace = "com.ander.pcflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ander.pcflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
    }

    signingConfigs {
        if (chaves.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(chaves.getProperty("storeFile"))
                storePassword = chaves.getProperty("storePassword")
                keyAlias = chaves.getProperty("keyAlias")
                keyPassword = chaves.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (chaves.getProperty("storeFile") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // org.json real na JVM: no unitTests do Android o org.json do SDK é um stub.
    testImplementation("org.json:json:20240303")
}
