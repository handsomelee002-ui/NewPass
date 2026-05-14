import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProperty("NEWPASS_RELEASE_STORE_FILE")
val releaseStorePassword = signingProperty("NEWPASS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingProperty("NEWPASS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProperty("NEWPASS_RELEASE_KEY_PASSWORD")
val releaseSignatureSha256 = signingProperty("NEWPASS_RELEASE_SIGNATURE_SHA256")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
    releaseSignatureSha256
).all { !it.isNullOrBlank() }

android {
    namespace = "com.gero.newpass"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gero.newpass"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "OFFICIAL_SIGNATURE_SHA256",
            "\"${releaseSignatureSha256 ?: "REPLACE_ME_WITH_YOUR_RELEASE_FINGERPRINT"}\""
        )
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            //vcsInfo.include = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    /*
      this configuration is used to make the build output (APK or AAB) smaller by
      excluding information about the project's dependencies
     */
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

tasks.matching { task ->
    task.name in setOf("packageRelease", "assembleRelease", "bundleRelease")
}.configureEach {
    doFirst {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Release signing is not configured. Set NEWPASS_RELEASE_STORE_FILE, " +
                    "NEWPASS_RELEASE_STORE_PASSWORD, NEWPASS_RELEASE_KEY_ALIAS, " +
                    "NEWPASS_RELEASE_KEY_PASSWORD, and NEWPASS_RELEASE_SIGNATURE_SHA256 " +
                    "in local.properties or environment variables."
            )
        }
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity:1.9.3")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    implementation("com.daimajia.androidanimations:library:2.4@aar")
}
