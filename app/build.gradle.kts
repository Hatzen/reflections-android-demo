import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.serializers.JsonSerializer
import org.reflections.serializers.XmlSerializer
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import java.io.File
import java.net.URLClassLoader

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

abstract class GenerateReflectionsIndexTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allJars: ListProperty<RegularFile>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val urls = (allDirectories.get().map { it.asFile } + allJars.get().map { it.asFile })
            .map { it.toURI().toURL() }
            .toTypedArray()

        val reflectionsOutDir = outputDir.get().dir("META-INF/reflections").asFile
        reflectionsOutDir.mkdirs()

        URLClassLoader(urls, Reflections::class.java.classLoader).use { classLoader ->
            val reflections = Reflections(
                ConfigurationBuilder()
                    .forPackage("de.hartz.software.reflectionsdemo.showcase", classLoader)
                    .filterInputsBy(FilterBuilder().includePackage("de.hartz.software.reflectionsdemo.showcase"))
                    .addClassLoaders(classLoader)
                    .setScanners(
                        Scanners.SubTypes,
                        Scanners.TypesAnnotated,
                        Scanners.MethodsAnnotated,
                        Scanners.FieldsAnnotated
                    )
            )
            reflections.save(File(reflectionsOutDir, "demo-reflections.xml").absolutePath, XmlSerializer())
            reflections.save(File(reflectionsOutDir, "demo-reflections.json").absolutePath, JsonSerializer())
        }
    }
}

android {
    namespace = "de.hartz.software.reflectionsdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.hartz.software.reflectionsdemo"
        minSdk = 24
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
            excludes += listOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val taskName = "generate${variant.name.replaceFirstChar { it.uppercase() }}ReflectionsIndex"
        val taskProvider = tasks.register<GenerateReflectionsIndexTask>(taskName)
        variant.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .use(taskProvider)
            .toGet(
                ScopedArtifact.CLASSES,
                GenerateReflectionsIndexTask::allJars,
                GenerateReflectionsIndexTask::allDirectories
            )
        variant.sources.resources?.addGeneratedSourceDirectory(
            taskProvider,
            GenerateReflectionsIndexTask::outputDir
        )
    }
}

dependencies {
    implementation("com.github.Hatzen:reflections:8dc2bbd")
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

