package de.hartz.software.reflectionsdemo

import androidx.test.platform.app.InstrumentationRegistry
import de.hartz.software.reflectionsdemo.showcase.DemoAction
import de.hartz.software.reflectionsdemo.showcase.DemoPlugin
import de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature
import de.hartz.software.reflectionsdemo.showcase.ShowcaseNoteDocument
import io.jsondb.JsonDBTemplate
import io.jsondb.query.Update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.reflections.Reflections
import org.reflections.serializers.JsonSerializer
import org.reflections.util.FilterBuilder
import java.io.File

class ReflectionsShowcaseInstrumentedTest {

    @Test
    fun showcaseRunsOnAndroidDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.cacheDir, "jsondb-instrumented").apply {
            deleteRecursively()
            mkdirs()
        }
        val result = ReflectionsShowcaseRunner.run(context.classLoader, dbDir)

        assertTrue("Expected collect() & JsonDB checks to pass:\n${result.formatReport()}", result.isAllWorking)
        assertTrue(result.jsonDbCollectionDiscovered)
        assertEquals(0, result.directRuntimeScanSubtypesCount)
    }

    @Test
    fun defaultReflectionsCollectWorksOnAndroidWithoutExplicitClassLoader() {
        val xmlDefault = Reflections.collect()
        val subtypes = xmlDefault.getSubTypesOf(ShowcaseFeature::class.java).map { it.name }.toSet()
        assertEquals(
            setOf(
                "de.hartz.software.reflectionsdemo.showcase.AlphaFeature",
                "de.hartz.software.reflectionsdemo.showcase.BetaFeature"
            ),
            subtypes
        )
        assertEquals(3, xmlDefault.getTypesAnnotatedWith(DemoPlugin::class.java).size)
        assertEquals(2, xmlDefault.getMethodsAnnotatedWith(DemoAction::class.java).size)

        val jsonCollected = Reflections.collect(
            "META-INF/reflections/",
            FilterBuilder().includePattern(".*-reflections\\.json"),
            JsonSerializer()
        )
        assertEquals(subtypes, jsonCollected.getSubTypesOf(ShowcaseFeature::class.java).map { it.name }.toSet())
    }

    @Test
    fun standardJsonDbTemplateDiscoversEntitiesAndRunsCrudOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.cacheDir, "jsondb-standard-api").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            // Standard JsonDBTemplate(dbFilesLocation, baseScanPackage) without custom classLoader or entityClasses
            val jsonDb = JsonDBTemplate(
                dbDir.absolutePath,
                "de.hartz.software.reflectionsdemo.showcase"
            )

            jsonDb.createCollection(ShowcaseNoteDocument::class.java)
            assertTrue(jsonDb.collectionExists(ShowcaseNoteDocument::class.java))

            val initial = ShowcaseNoteDocument("note-1", "Initial Title", "AndroidTest")
            jsonDb.insert<ShowcaseNoteDocument>(initial)

            val byId = jsonDb.findById("note-1", ShowcaseNoteDocument::class.java)
            assertNotNull(byId)
            assertEquals("Initial Title", byId?.title)

            val viaQuery = jsonDb.find("/.[id='note-1']", ShowcaseNoteDocument::class.java)
            assertEquals(1, viaQuery.size)
            assertEquals("AndroidTest", viaQuery[0].source)

            val modified = jsonDb.findAndModify<ShowcaseNoteDocument>(
                "/.[id='note-1']",
                Update.update("title", "Updated via findAndModify"),
                ShowcaseNoteDocument::class.java
            )
            assertNotNull(modified)
            assertEquals("Updated via findAndModify", modified?.title)

            val reloaded = jsonDb.findById("note-1", ShowcaseNoteDocument::class.java)
            assertEquals("Updated via findAndModify", reloaded?.title)

            jsonDb.remove<ShowcaseNoteDocument>(reloaded, ShowcaseNoteDocument::class.java)
            assertNull(jsonDb.findById("note-1", ShowcaseNoteDocument::class.java))

            jsonDb.dropCollection(ShowcaseNoteDocument::class.java)
            assertFalse(jsonDb.collectionExists(ShowcaseNoteDocument::class.java))
        } finally {
            dbDir.deleteRecursively()
        }
    }
}
