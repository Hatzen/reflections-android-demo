package de.hartz.software.reflectionsdemo

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
