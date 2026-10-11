package io.github.mothlight.sample.android.weather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.*
import io.github.mothlight.android.*
import io.github.mothlight.compose.*
import io.github.mothlight.okhttp.MothlightInterceptor
import io.github.mothlight.room.RoomDatabaseSource
import io.github.mothlight.sample.android.MainActivity
import io.github.mothlight.sample.android.R
import io.github.mothlight.sqlite.TracingSQLiteDriver
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import okhttp3.OkHttpClient

/** Integration lives here: one recorder shared by HTTP, SQL, database browser and Logcat. */
class WeatherActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true))
    private val panelState = MothlightPanelState()
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(MothlightInterceptor(mothlight.network))
        .build()
    private lateinit var database: WeatherDatabase
    private lateinit var repository: WeatherRepository
    private lateinit var status: TextView
    private lateinit var logcatStatus: TextView
    private lateinit var refresh: Button
    private lateinit var city: Spinner
    private lateinit var capture: LogcatCapture
    private lateinit var weatherDetails: LinearLayout
    private lateinit var expandInspector: Button
    private var logcatJob: Job = Job().apply { complete() }
    private var refreshJob: Job = Job().apply { complete() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mothlight.setEnabled(true)
        database = Room.databaseBuilder(this, WeatherDatabase::class.java, "mothlight-weather.db")
            .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
            .setQueryCoroutineContext(Dispatchers.IO).build()
        repository = WeatherRepository(weatherRemote(client), RoomWeatherCache(database.weather())) { message ->
            // Controlled labels only: no coordinates, response body or exception text.
            Log.i("MothlightWeather", message)
        }
        val tools = mothlightTools(mothlight) { tool(databaseTool(RoomDatabaseSource(database))) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            // Native controls and Compose panel both stay within system bars on target SDK 36.
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(padding + insets.systemWindowInsetLeft, padding + insets.systemWindowInsetTop,
                    padding + insets.systemWindowInsetRight, padding + insets.systemWindowInsetBottom)
                insets.consumeSystemWindowInsets()
            }
        }
        layout.addView(TextView(this).apply { setText(R.string.weather_title); textSize = 22f })
        weatherDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(weatherDetails)
        val controls = LinearLayout(this)
        city = Spinner(this).apply {
            adapter = ArrayAdapter(this@WeatherActivity, android.R.layout.simple_spinner_dropdown_item,
                WeatherCity.entries.map { cityName(it.name) })
            contentDescription = getString(R.string.weather_city)
        }
        controls.addView(city, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        refresh = Button(this).apply { setText(R.string.weather_refresh); setOnClickListener { refreshWeather() } }
        controls.addView(refresh)
        weatherDetails.addView(controls)
        status = TextView(this).apply { setText(R.string.weather_ready) }
        weatherDetails.addView(status)
        val history = TextView(this).apply { setText(R.string.weather_empty); textSize = 16f }
        weatherDetails.addView(history)
        weatherDetails.addView(TextView(this).apply {
            setText(R.string.weather_attribution)
            setTextColor(android.graphics.Color.rgb(70, 40, 120))
            setPadding(0, 8, 0, 8)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) }
        })
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply {
            setText(R.string.weather_logs)
            setOnClickListener { Log.w("MothlightWeather", "demo_warning:user_requested"); panelState.selectTool("logs"); showInspector(true) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            setText(R.string.weather_diagnostics)
            setOnClickListener { startActivity(Intent(this@WeatherActivity, MainActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        weatherDetails.addView(actions)
        expandInspector = Button(this).apply {
            setText(R.string.weather_expand_panel)
            setOnClickListener { showInspector(weatherDetails.visibility == View.VISIBLE) }
        }
        layout.addView(expandInspector)
        logcatStatus = TextView(this)
        layout.addView(logcatStatus)
        layout.addView(createMemoryOverlayView(this, mothlight))
        layout.addView(createMothlightView(this, mothlight, tools, panelState),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        layout.requestApplyInsets()
        scope.launch {
            repository.history().catch { failure ->
                if (failure is CancellationException) throw failure
                status.setText(R.string.weather_storage_failed)
                Log.e("MothlightWeather", "cache_read_failed")
            }.collect { rows ->
                history.text = if (rows.isEmpty()) getString(R.string.weather_empty) else rows.take(3).joinToString("\n") {
                    getString(R.string.weather_reading, cityName(it.city), it.temperature, it.wind, it.observedAt)
                }
            }
        }
    }
    private fun showInspector(expanded: Boolean) {
        weatherDetails.visibility = if (expanded) View.GONE else View.VISIBLE
        expandInspector.setText(if (expanded) R.string.weather_show_weather else R.string.weather_expand_panel)
    }
    private fun refreshWeather() {
        if (refreshJob.isActive) return
        refreshJob = scope.launch {
            refresh.isEnabled = false
            status.setText(R.string.weather_loading)
            try {
                val result = repository.refresh(WeatherCity.entries[city.selectedItemPosition])
                status.setText(when (result) {
                    WeatherUpdate.SAVED -> R.string.weather_saved
                    WeatherUpdate.NETWORK_FAILED -> R.string.weather_network_failed
                    WeatherUpdate.STORAGE_FAILED -> R.string.weather_storage_failed
                })
            } finally { refresh.isEnabled = true }
        }
    }
    private fun cityName(name: String): String = getString(when (name) {
        "LISBON" -> R.string.city_lisbon
        "TOKYO" -> R.string.city_tokyo
        else -> R.string.city_novosibirsk
    })
    override fun onStart() {
        super.onStart()
        capture = captureLogcat(scope, mothlight, tags = setOf("MothlightWeather"), sanitize = { it })
        logcatJob = scope.launch {
            capture.status.collect { state ->
                logcatStatus.setText(when (state) {
                    LogcatStatus.READING -> R.string.logcat_reading
                    LogcatStatus.STOPPED -> R.string.logcat_stopped
                    LogcatStatus.UNAVAILABLE -> R.string.logcat_unavailable
                })
            }
        }
    }
    override fun onStop() {
        capture.close()
        logcatJob.cancel()
        super.onStop()
    }
    override fun onDestroy() {
        scope.cancel()
        database.close()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
