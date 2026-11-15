package de.hartz.software.reflectionsdemo

import de.hartz.software.reflectionsdemo.showcase.AlphaFeature
import de.hartz.software.reflectionsdemo.showcase.BetaFeature
import de.hartz.software.reflectionsdemo.showcase.ExtraAnnotatedComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReflectionsShowcaseUnitTest {

    @Test
    fun generatedMetadataIndexCollectsAndRunsJsonDbInSimulatedDirectoryLessApk() {
        val classLoader = javaClass.classLoader!!
        val jsonUrl = classLoader.getResource("META-INF/reflections/demo-reflections.json")
        val xmlUrl = classLoader.getResource("META-INF/reflections/demo-reflections.xml")

        requireNotNull(jsonUrl) { "Expected generated META-INF/reflections/demo-reflections.json on test classpath" }
        requireNotNull(xmlUrl) { "Expected generated META-INF/reflections/demo-reflections.xml on test classpath" }

        val tempDir = Files.createTempDirectory("apk-sim").toFile()
        try {
            val apkFile = File(tempDir, "app-release.apk")
            ZipOutputStream(apkFile.outputStream()).use { zos ->
                fun put(name: String, bytes: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
                put("AndroidManifest.xml", "<manifest package=\"de.hartz.software.reflectionsdemo\"/>".toByteArray(StandardCharsets.UTF_8))
                put("META-INF/reflections/demo-reflections.json", jsonUrl.openStream().use { it.readBytes() })
                put("META-INF/reflections/demo-reflections.xml", xmlUrl.openStream().use { it.readBytes() })
            }

            URLClassLoader(arrayOf(apkFile.toURI().toURL()), null).use { resourceDelegate ->
                val androidLikeLoader = object : ClassLoader(classLoader) {
                    override fun getResources(name: String): Enumeration<URL> = resourceDelegate.getResources(name)
                    override fun getResource(name: String?): URL? = resourceDelegate.getResource(name)
                }

                // Directory entry lookup returns empty in directory-less APK
                assertFalse(androidLikeLoader.getResources("META-INF/reflections/").hasMoreElements())

                val dbDir = File(tempDir, "jsondb")
                val result = ReflectionsShowcaseRunner.run(androidLikeLoader, dbDir)
                assertTrue("Expected all build-time collect & JsonDB checks to pass:\n${result.formatReport()}", result.isAllWorking)
                assertTrue("Expected JsonDB collection showcaseNotes to be created", result.jsonDbCollectionDiscovered)
                assertEquals(1, result.jsonDbStoredNotes.size)
                assertTrue(File(dbDir, "showcaseNotes.json").isFile)
                assertEquals(
                    setOf(AlphaFeature::class.java.name, BetaFeature::class.java.name),
                    result.jsonSubtypes
                )
                assertEquals(
                    setOf(
                        AlphaFeature::class.java.name,
                        BetaFeature::class.java.name,
                        ExtraAnnotatedComponent::class.java.name
                    ),
                    result.jsonAnnotatedTypes
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
