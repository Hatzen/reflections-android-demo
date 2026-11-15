package de.hartz.software.reflectionsdemo

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reportText = try {
            val dbDir = File(filesDir, "jsondb-showcase")
            val result = ReflectionsShowcaseRunner.run(classLoader, dbDir)
            result.formatReport()
        } catch (t: Throwable) {
            Log.e(TAG, "Showcase failed", t)
            "FAILED: ${t::class.java.name}: ${t.message}\n\n${Log.getStackTraceString(t)}"
        }

        Log.i(TAG, reportText)

        val textView = TextView(this).apply {
            text = reportText
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(40, 40, 40, 40)
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        setContentView(scrollView)
    }

    companion object {
        private const val TAG = "ReflectionsDemo"
    }
}
