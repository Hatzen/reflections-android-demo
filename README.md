# Reflections Android Demo (Fork + Gradle Plugin + JsonDB + ProGuard/R8)

An end-to-end Android application demonstrating how to run [`org.reflections:reflections`](https://github.com/ronmamo/reflections), `gradle-reflections-plugin`, and `jsondb-core` on Android—including **ProGuard / R8 minification enabled (`isMinifyEnabled = true`)**—using the Android/Gradle fixes from:

- [`Hatzen/reflections`](https://github.com/Hatzen/reflections/commit/8dc2bbd) (fixes [`ronmamo/reflections#127`](https://github.com/ronmamo/reflections/issues/127))
- `Hatzen/gradle-reflections-plugin` (fixes [`manosbatsis/gradle-reflections-plugin#5`](https://github.com/manosbatsis/gradle-reflections-plugin/issues/5))
- `Hatzen/jsondb-core` (fixes [`Jsondb/jsondb-core#42`](https://github.com/Jsondb/jsondb-core/issues/42) and [`Jsondb/jsondb-core#53`](https://github.com/Jsondb/jsondb-core/issues/53))

---

## Why stock libraries failed on Android (and what the forks fix)

1. **`ronmamo/reflections` (`Hatzen/reflections@8dc2bbd`):**
   - Directory-less Gradle JARs and `.apk` archives return no directory entries for `ClassLoader.getResources("META-INF/reflections/")`, and `PathClassLoader` is not a `URLClassLoader`.
   - `JsonSerializer.save(...)` used `java.nio.file.Files`, failing on older Android APIs.
   - `Reflections.collect(...)` needed a `ClassLoader...` overload, JSON/XML serializer auto-detection, and `AndroidManifest.xml` / `META-INF/MANIFEST.MF` URL fallback.

2. **`manosbatsis/gradle-reflections-plugin` (`Hatzen/gradle-reflections-plugin`):**
   - Original plugin hardcoded Gradle 4 `sourceSets.main.java.outputDir` (broken on Gradle 5+ and absent on Android projects).
   - Updated to Gradle 8 / JDK 17, uses `sourceSets.main.output.classesDirs` on JVM, supports explicit `inputFiles`/`outputDirectory` + `.json`/`.xml` outputs, and automatically wires AGP 8+ variant tasks (`ScopedArtifact.CLASSES` + `variant.sources.resources.addGeneratedSourceDirectory`) whenever `com.android.base` is applied.

3. **`Jsondb/jsondb-core` (`Hatzen/jsondb-core`):**
   - Original `CollectionMetaData.builder(dbConfig)` called `new Reflections(baseScanPackage)` without fallback to pre-built indexes or configured `ClassLoader`s, throwing `InvalidJsonDbApiUsageException: No class found with @Document Annotation...` on Android.
   - `Util.java` imported unused `java.beans.XMLDecoder`/`XMLEncoder` and `JsonDBTemplate` called `BeanUtils.copyProperty` (`java.beans.Introspector` is absent on Android).
   - Fixed to fall back cleanly to `Reflections.collect(...)` (`META-INF/reflections/*.xml` & `*.json`) filtered by `baseScanPackage` (plus optional `entityClasses` and `ClassLoader...` overrides), removed the unused `java.beans` imports, and added a reflection setter fallback when `java.beans.Introspector` is unavailable.

---

## What Works vs. What Does NOT Work on Android

| Capability | Status on Android | Notes |
| --- | --- | --- |
| Build-time AGP indexing (`io.github.manosbatsis.gradle.plugin.reflections`) | **Works** | Declarative `reflectionsConfig { ... }` DSL generates `.xml` / `.json` via `ScopedArtifact.CLASSES` |
| Runtime `Reflections.collect(...)` with **JSON** or **XML** | **Works** | Pass `context.classLoader`; works with R8/ProGuard enabled |
| `JsonDBTemplate` `@Document` discovery, `createCollection`, `upsert`, `findAll` | **Works (`minSdk 26+`)** | Discovers `@Document` classes via `gradle-reflections-plugin` index; `minSdk 26` provides `java.nio.file` used by JsonDB |
| Direct runtime scanning (`new Reflections("com.example")`) | **Returns 0 classes** | Android APKs store `.dex`, not `.class` files; showcased live in `MainActivity` |

---

## Gradle Setup (`gradle-reflections-plugin` + `jsondb-core`)

While the forked `gradle-reflections-plugin` and `jsondb-core` are published locally via `./gradlew publishToMavenLocal`, [`settings.gradle.kts`](settings.gradle.kts), [`build.gradle.kts`](build.gradle.kts), and [`app/build.gradle.kts`](app/build.gradle.kts) resolve them from `mavenLocal()`.

Once pushed to `https://github.com/Hatzen/gradle-reflections-plugin` and `https://github.com/Hatzen/jsondb-core`, switch to JitPack coordinates directly (see comments in [`settings.gradle.kts`](settings.gradle.kts) and [`app/build.gradle.kts`](app/build.gradle.kts)).

### Plugin configuration (`app/build.gradle.kts`)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.manosbatsis.gradle.plugin.reflections")
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
```

### Runtime showcase & JsonDB integration (`ReflectionsShowcaseRunner.kt`)

`ReflectionsShowcaseRunner.run(classLoader, dbDir)` verifies:
1. Pre-scanned JSON & XML `Reflections.collect(...)` queries (`ShowcaseFeature`, `@DemoPlugin`, `@DemoAction`, `@DemoTag`).
2. Reflective instantiation under ProGuard/R8.
3. Real `JsonDBTemplate` collection auto-discovery and persistence for [`ShowcaseNoteDocument`](app/src/main/java/de/hartz/software/reflectionsdemo/showcase/ShowcaseNoteDocument.java) (`@Document(collection = "showcaseNotes")`).

---

## ProGuard / R8 Configuration (`app/proguard-rules.pro`)

Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`. Keep target `@DemoPlugin`, `ShowcaseFeature`, and `@Document` entities in [`app/proguard-rules.pro`](app/proguard-rules.pro):

```proguard
-keep @de.hartz.software.reflectionsdemo.showcase.DemoPlugin class * {
    <init>(...);
    @de.hartz.software.reflectionsdemo.showcase.DemoAction <methods>;
    @de.hartz.software.reflectionsdemo.showcase.DemoTag <fields>;
}
-keep class * implements de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature {
    <init>(...);
}

# Keep JsonDB @Document entities and runtime classes under R8
-keep @io.jsondb.annotation.Document class * {
    <init>(...);
    <fields>;
    <methods>;
}
-keep class io.jsondb.** { *; }
-dontwarn io.jsondb.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.**

-keep class org.reflections.** { *; }
-dontwarn org.reflections.**
```

---

## Build & Verify Locally

```bash
# Publish local plugin & jsondb-core forks to mavenLocal() first (until pushed to GitHub/JitPack)
(cd ../gradle-reflections-plugin && ./gradlew publishToMavenLocal)
(cd ../jsondb-core && ./gradlew publishToMavenLocal)

# Run unit tests (verifies plugin-generated index + JsonDBTemplate inside simulated directory-less APK)
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build Debug APK, ProGuard/R8 Release APK, AAB, and AndroidTest APK
./gradlew assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest
```
