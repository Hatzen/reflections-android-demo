buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.github.Hatzen:reflections:8dc2bbd")
        classpath("com.google.code.gson:gson:2.10.1")
        classpath("org.dom4j:dom4j:2.1.3") {
            exclude(group = "pull-parser", module = "pull-parser")
            exclude(group = "xpp3", module = "xpp3")
            exclude(group = "xml-apis", module = "xml-apis")
        }
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
}

