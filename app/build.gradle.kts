plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("kotlin-android")
}

android {
    namespace = "com.example.dermascanai"
    compileSdk = 35

//    signingConfigs {
//        create("release") {
//            storeFile = file(project.properties["RELEASE_STORE_FILE"] as String)
//            storePassword = project.properties["RELEASE_STORE_PASSWORD"] as String
//            keyAlias = project.properties["RELEASE_KEY_ALIAS"] as String
//            keyPassword = project.properties["RELEASE_KEY_PASSWORD"] as String
//        }
//    }

    defaultConfig {
        applicationId = "com.example.dermascanai"
        minSdk = 27
        targetSdk = 35
        versionCode = 4
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Keep only English resources (change if you need others)
        resConfigs("en")
    }

    buildTypes {
        release {
            // ✅ Shrink + optimize for smaller APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ✅ Align APK for better compression
//            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug builds don’t need shrinking
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // ✅ Split APKs by ABI (device gets only needed CPU arch)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a") // most common
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        mlModelBinding = true
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)

    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.27")
    implementation("com.prolificinteractive:material-calendarview:1.4.3")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("androidx.palette:palette:1.0.0")

    implementation("com.github.bumptech.glide:glide:4.15.0")
    ksp("com.github.bumptech.glide:compiler:4.15.0")

//---MAP API---
    implementation ("org.osmdroid:osmdroid-android:6.1.14")
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")
//-----------

    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.3")

    implementation("androidx.room:room-runtime:2.5.0")
    ksp("androidx.room:room-compiler:2.5.0")
    ksp("com.google.dagger:hilt-compiler:2.40.5")

    implementation("com.google.android.material:material:1.11.0")
//    implementation ("com.google.mlkit:segmentation-selfie:17.0.6")
//
//    // Optional: for using InputImage
//    implementation ("com.google.mlkit:vision-common:17.2.0")
    // ✅ TensorFlow Lite stable
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
