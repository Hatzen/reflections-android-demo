import org.reflections.scanners.Scanners

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.manosbatsis.gradle.plugin.reflections")
}

android {
    namespace = "de.hartz.software.reflectionsdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.hartz.software.reflectionsdemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            val customKeystore = keystorePath?.let { file(it) }
            if (customKeystore != null && customKeystore.exists()) {
                storeFile = customKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES*"
            )
        }
    }
}

reflectionsConfig {
    packages("de.hartz.software.reflectionsdemo.showcase")
    indexFilename = "META-INF/reflections/demo-reflections.xml"
    additionalIndexFilenames("META-INF/reflections/demo-reflections.json")
    setParams(
        Scanners.SubTypes,
        Scanners.TypesAnnotated,
        Scanners.MethodsAnnotated,
        Scanners.FieldsAnnotated
    )
}

dependencies {
    implementation("com.github.Hatzen:reflections:0.10.2-android.1")
    implementation("com.github.Hatzen:jsondb-core:1.0.132-android.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.dom4j:dom4j:2.1.3") {
        exclude(group = "pull-parser", module = "pull-parser")
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "xml-apis", module = "xml-apis")
    }

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
