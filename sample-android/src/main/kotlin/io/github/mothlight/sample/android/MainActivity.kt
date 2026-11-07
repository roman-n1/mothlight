package io.github.mothlight.sample.android

import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.room.RoomDatabaseSource
import io.github.mothlight.sqlite.TracingSQLiteDriver
import io.github.mothlight.compose.*
import kotlinx.coroutines.*
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import io.github.mothlight.*
import io.github.mothlight.android.createMothlightView
import io.github.mothlight.android.createMemoryOverlayView
import io.github.mothlight.okhttp.MothlightInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.atomic.AtomicLong

/** Native Android Views + Retrofit. No Compose plugin or composable functions in this app. */
class MainActivity : ComponentActivity() {
    interface DemoApi { @GET("items?token=demo-secret") fun items(): Call<ResponseBody> }
    private val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true))
    private val client = OkHttpClient.Builder()
        .addInterceptor(MothlightInterceptor(mothlight.network))
        // Terminal mock interceptor: the demo never contacts a real server.
        .addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"message":"Hello Android","access_token":"demo-secret"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
    private val api = Retrofit.Builder().baseUrl("https://demo.mothlight.test/").client(client).build().create(DemoApi::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val panelState = MothlightPanelState()
    private lateinit var database: DemoDatabase
    private var sequence = 0L
    private val logSequence = AtomicLong()
    private var pending: Call<ResponseBody>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mothlight.setEnabled(true)
        mothlight.startup.mark("activity_created", SystemClock.elapsedRealtime())
        database = Room.inMemoryDatabaseBuilder(this, DemoDatabase::class.java)
            .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
            .setQueryCoroutineContext(Dispatchers.IO).build()
        mothlight.startup.mark("room_configured", SystemClock.elapsedRealtime())
        val tools = MothlightTools(defaultMothlightTools(mothlight).entries + databaseTool(RoomDatabaseSource(database)))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val requestButton = Button(this).apply {
            setText(R.string.mock_request)
            setOnClickListener { request() }
        }
        layout.addView(requestButton)
        layout.addView(Button(this).apply {
            setText(R.string.add_row)
            setOnClickListener {
                val id = ++sequence
                scope.launch {
                    database.items().insert(DemoItem(id, "Demo item #$id", "demo-secret"))
                    demoLog(LogLevel.INFO, "Room", "Inserted demo item #$id")
                }
            }
        })
        layout.addView(createMemoryOverlayView(this, mothlight))
        layout.addView(createMothlightView(this, mothlight, tools, panelState), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        layout.doOnPreDraw {
            mothlight.startup.mark("first_layout", SystemClock.elapsedRealtime())
            demoLog(LogLevel.INFO, "Startup", "Inspector layout is ready")
        }
        setContentView(layout)
    }
    private fun request() {
        pending?.cancel()
        pending = api.items().also { call ->
            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {
                    demoLog(LogLevel.INFO, "Retrofit", "Demo response: HTTP ${response.code()}")
                    response.body()?.close()
                    response.errorBody()?.close()
                }
                override fun onFailure(call: Call<ResponseBody>, error: Throwable) {
                    if (!call.isCanceled) demoLog(LogLevel.WARN, "Retrofit", "Demo request failed")
                }
            })
        }
    }
    private fun demoLog(level: LogLevel, tag: String, message: String) {
        mothlight.logs.record(LogEntry(logSequence.incrementAndGet().toString(), SystemClock.elapsedRealtime(), level, tag, message))
    }

    override fun onDestroy() {
        pending?.cancel()
        scope.cancel()
        database.close()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
