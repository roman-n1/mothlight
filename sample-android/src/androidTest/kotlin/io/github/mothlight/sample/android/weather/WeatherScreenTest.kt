package io.github.mothlight.sample.android.weather

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mothlight.sample.android.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherScreenTest {
    @Test fun nativeHostOpensPanelAndSurvivesRecreation() {
        ActivityScenario.launch(WeatherActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                fun labels(view: View): List<CharSequence> = when (view) {
                    is TextView -> listOf(view.text)
                    is ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val labels = labels(activity.window.decorView)
                assertTrue(labels.contains(activity.getString(R.string.weather_title)))
                assertTrue(labels.contains(activity.getString(R.string.weather_refresh)))
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(directory, "weather.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
    }
}
