plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.novay.sound"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novay.sound"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.11.4"
        vectorDrawables { useSupportLibrary = true }
        // Sadece 64-bit ARM (tüm güncel telefonlar): ML Kit'in diğer işlemci kütüphaneleri APK'yı ~70 MB yapıyordu
        ndk { abiFilters += "arm64-v8a" }
    }

    // Sabit imza anahtarı: güncellemelerin eskisinin üstüne kurulabilmesi için şart.
    // CI'da KEYSTORE_BASE64 + KEYSTORE_PASSWORD secret'ları app/release.jks'ye çözülür.
    val releaseKeystore = rootProject.file("app/release.jks")
    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "novaysound"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        // NewPipeExtractor java.nio kullanıyor -> desugaring şart
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric
        unitTests.isReturnDefaultValues = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // AndroidX + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Media3 (ExoPlayer + MediaLibraryService + SimpleCache)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.database)
    implementation(libs.media3.cast)
    implementation(libs.media3.hls)
    implementation(libs.mediarouter)
    implementation(libs.reorderable)
    implementation(libs.zxing)
    // Sideload kurulumda da açılışı hızlandıran profil (src/main/baseline-prof.txt)
    implementation(libs.profileinstaller)
    // Söz çevirisi: telefonda çalışan ML Kit (internetsiz, ücretsiz)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testler
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ayarlar
    implementation(libs.androidx.datastore.preferences)

    // YouTube kaynağı
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)

    // Görseller + dinamik renk
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette)

    // Ana ekran widget'ı + haftalık otomatik yedek
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)

    // Coroutines + JSON yedekleme
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)
}
