package de.hartz.software.reflectionsdemo

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionsShowcaseInstrumentedTest {

    @Test
    fun showcaseRunsOnAndroidDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = ReflectionsShowcaseRunner.run(context.classLoader)

        assertTrue("Expected collect() queries to pass:\n${result.formatReport()}", result.isAllWorking)
        assertEquals(0, result.directRuntimeScanSubtypesCount)
    }
}

