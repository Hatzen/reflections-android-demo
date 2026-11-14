# Reflections Android Demo (Fork + JitPack + ProGuard/R8)

A minimal, end-to-end Android application demonstrating how to run [`org.reflections:reflections`](https://github.com/ronmamo/reflections) on Android—including **ProGuard / R8 minification enabled (`isMinifyEnabled = true`)**—using the Android/Gradle fixes from [`Hatzen/reflections`](https://github.com/Hatzen/reflections/commit/8dc2bbd) via **JitPack**.

Related upstream issues:
- [`ronmamo/reflections#127`](https://github.com/ronmamo/reflections/issues/127)
- [`manosbatsis/gradle-reflections-plugin#5`](https://github.com/manosbatsis/gradle-reflections-plugin/issues/5)

---

## Why stock `ronmamo/reflections` fails on Android (and what the fork fixes)

1. **Android APKs do not contain `.class` files:** D8/R8 compiles bytecode into `.dex` (`classes.dex`), and Android's `PathClassLoader` / `BaseDexClassLoader` is not a `URLClassLoader`.
2. **Pre-scanning + `Reflections.collect(...)` failed in stock Reflections** due to several bugs fixed in `Hatzen/reflections@8dc2bbd`:
   - `new Reflections(pkg, classLoader)` threw `ReflectionsException` (`ConfigurationBuilder.build` didn't accept `ClassLoader` after flattening parameters).
   - Directory-less Gradle JARs and `.apk` archives return no directory entries for `ClassLoader.getResources("META-INF/reflections/")`, causing `forPackage` / `collect` to return empty stores.
   - `Vfs` did not recognize `.apk` / `.aar` archive URLs cleanly, mishandled leading `/` or exact file prefixes, and leaked `Vfs.Dir` (`JarFile`) handles.
   - `JsonSerializer.save(...)` used `Files.write(file.toPath(), ...)`, which crashes on Android API `< 26`.
   - `Reflections.collect(...)` had no `ClassLoader...` overload for Android `context.classLoader`, hardcoded `XmlSerializer` even for `.json`, and did not fall back via `AndroidManifest.xml` / `META-INF/MANIFEST.MF` inside directory-less APKs.

---

## What Works vs. What Does NOT Work on Android

| Capability | Status on Android | Notes |
| --- | --- | --- |
| Build-time Gradle class scanning (`new Reflections(cfg)`) | **Works** | Run in Gradle on host JVM via AGP `ScopedArtifact.CLASSES` before dexing |
| Runtime `Reflections.collect(...)` with **JSON** (`JsonSerializer`) | **Works** | Recommended for Android (`minSdk 24+` and lighter than XML; requires `gson`) |
| Runtime `Reflections.collect(...)` with **XML** (`XmlSerializer`) | **Works** | Requires `org.dom4j:dom4j` dependency |
| Queries (`getSubTypesOf`, `getTypesAnnotatedWith`, `getMethodsAnnotatedWith`, `getFieldsAnnotatedWith`) + `newInstance()` | **Works** | Even with **R8 / ProGuard** enabled when `-keep` rules are configured |
| Direct runtime scanning (`new Reflections("com.example")`) | **Does NOT work (returns 0 classes)** | Android packages `.dex` bytecode, not `.class` files; showcased live in `MainActivity` |
| `JavaCodeSerializer` on Android `< API 26` | **Not supported** | Uses `java.nio.file.Files` (use `JsonSerializer` or `XmlSerializer` instead) |
| Stock `manosbatsis/gradle-reflections-plugin` | **Does NOT work on Android** | Assumes JVM `sourceSets.main.java.outputDir` rather than AGP `androidComponents` |

---

## Gradle & JitPack Setup

### 1. Add JitPack (`settings.gradle.kts` & `build.gradle.kts`)

In `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In root `build.gradle.kts` (`buildscript` is needed so the build-time Gradle task can invoke `Reflections`):
```kotlin
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.github.Hatzen:reflections:8dc2bbd")
        classpath("com.google.code.gson:gson:2.10.1")
        // Exclude legacy XML pull-parser drivers so they don't hijack AGP's resource merger SAXParserFactory
        classpath("org.dom4j:dom4j:2.1.3") {
            exclude(group = "pull-parser", module = "pull-parser")
            exclude(group = "xpp3", module = "xpp3")
            exclude(group = "xml-apis", module = "xml-apis")
        }
    }
}
```

### 2. App dependencies (`app/build.gradle.kts`)

Notice `gson` and `dom4j` are `<optional>true</optional>` in Reflections' `pom.xml`, so declare whichever serializer(s) you use:

```kotlin
dependencies {
    implementation("com.github.Hatzen:reflections:8dc2bbd")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.dom4j:dom4j:2.1.3") {
        exclude(group = "pull-parser", module = "pull-parser")
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "xml-apis", module = "xml-apis")
    }
}
```

### 3. Build-time scanning task for AGP 8+ (`app/build.gradle.kts`)

Instead of relying on JVM `sourceSets`, wire an AGP variant task using `ScopedArtifact.CLASSES` + `variant.sources.resources?.addGeneratedSourceDirectory(...)`:

```kotlin
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
```

At build time, `GenerateReflectionsIndexTask` scans the compiled classes into `META-INF/reflections/demo-reflections.json` and `.xml`, and AGP packages them into both Debug and Release APKs/AABs.

### 4. Runtime collection in Android (`ReflectionsShowcaseRunner.kt`)

Always pass `context.classLoader` explicitly so Android's `PathClassLoader` resolves the APK resources reliably from any thread:

```kotlin
val jsonReflections = Reflections.collect(
    "META-INF/reflections/",
    FilterBuilder().includePattern(".*-reflections\\.json"),
    JsonSerializer(),
    classLoader
)

val features = jsonReflections.getSubTypesOf(ShowcaseFeature::class.java)
val annotated = jsonReflections.getTypesAnnotatedWith(DemoPlugin::class.java)
```

---

## ProGuard / R8 Configuration (`app/proguard-rules.pro`)

Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`. Because the pre-scanned index stores class, method, and field names as strings for `Class.forName(...)`, you **must** keep those target types/members in [`app/proguard-rules.pro`](app/proguard-rules.pro):

```proguard
-keep @de.hartz.software.reflectionsdemo.showcase.DemoPlugin class * {
    <init>(...);
    @de.hartz.software.reflectionsdemo.showcase.DemoAction <methods>;
    @de.hartz.software.reflectionsdemo.showcase.DemoTag <fields>;
}
-keep class * implements de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature {
    <init>(...);
}
-keep interface de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoPlugin { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoAction { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoTag { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class org.reflections.** { *; }
-dontwarn org.reflections.**

# Optional server/JVM-only deps referenced by Reflections & dom4j
-dontwarn javax.servlet.**
-dontwarn org.jboss.vfs.**
-dontwarn javassist.**
-dontwarn javax.annotation.**
-dontwarn org.jaxen.**
-dontwarn org.xmlpull.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.**
-dontwarn org.relaxng.**
-dontwarn org.slf4j.impl.**
```

---

## Build & Verify Locally

```bash
# Run unit tests (verifies generated metadata + simulated directory-less APK collect)
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build Debug APK and R8/ProGuard-minified Release APK + AAB
./gradlew assembleDebug assembleRelease bundleRelease
```

On CI (`.github/workflows/ci.yml`), every push to `main` builds Debug & ProGuard Release APKs/AABs, runs unit tests and lint, and verifies that `app-release.apk` contains `classes.dex`, `AndroidManifest.xml`, `META-INF/reflections/demo-reflections.json`, and `META-INF/reflections/demo-reflections.xml`.

