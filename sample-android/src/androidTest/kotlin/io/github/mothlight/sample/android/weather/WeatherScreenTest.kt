package io.github.mothlight.sample.android.weather

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mothlight.sample.android.R
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val directory = output?.let(::File) ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
            directory.mkdirs()
            fun screenshot(name: String) {
                if (Build.VERSION.SDK_INT >= 29) {
                    val committed = CountDownLatch(1)
                    scenario.onActivity { activity ->
                        val root = activity.window.decorView
                        // Wait for the rendered frame, not just a drained main-thread queue.
                        root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                        root.invalidate()
                    }
                    assertTrue("Screenshot frame was not committed", committed.await(10, TimeUnit.SECONDS))
                }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(bitmap)
                val file = File(directory, "$name.png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                instrumentation.sendStatus(2, Bundle().apply { putString("additionalTestOutputFile_$name", file.absolutePath) })
                bitmap.recycle()
            }
            screenshot("weather")
            fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                val expand = all.filterIsInstance<Button>().single { it.text == activity.getString(R.string.weather_expand_panel) }
                val refresh = all.filterIsInstance<Button>().single { it.text == activity.getString(R.string.weather_refresh) }
                assertTrue(refresh.isShown)
                expand.performClick()
                assertFalse(refresh.isShown)
                assertEquals(activity.getString(R.string.weather_show_weather), expand.text)
            }
            instrumentation.waitForIdleSync()
            screenshot("inspector")
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                all.filterIsInstance<Button>().single { it.text == activity.getString(R.string.weather_show_weather) }.performClick()
                assertTrue(all.filterIsInstance<Button>().single { it.text == activity.getString(R.string.weather_refresh) }.isShown)
            }
        }
    }
}
