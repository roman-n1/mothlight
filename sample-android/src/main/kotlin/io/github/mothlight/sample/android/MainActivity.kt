package io.github.mothlight.sample.android

import android.os.Bundle
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
import io.github.mothlight.*
import io.github.mothlight.android.createMothlightView
import io.github.mothlight.okhttp.MothlightInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.http.GET

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
    private lateinit var database: DemoDatabase
    private var sequence = 0L
    private var pending: Call<ResponseBody>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mothlight.setEnabled(true)
        database = Room.inMemoryDatabaseBuilder(this, DemoDatabase::class.java)
            .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
            .setQueryCoroutineContext(Dispatchers.IO).build()
        val tools = MothlightTools(defaultMothlightTools(mothlight).entries + databaseTool(RoomDatabaseSource(database)))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val requestButton = Button(this).apply {
            setText(R.string.mock_request)
            setOnClickListener { request() }
        }
        layout.addView(requestButton)
        layout.addView(Button(this).apply {
            setText(R.string.add_row_frame)
            setOnClickListener {
                val id = ++sequence
                scope.launch {
                    database.items().insert(DemoItem(id, "Demo item #$id", "demo-secret"))
                    val now = System.currentTimeMillis()
                    mothlight.realtime.connection("demo", ConnectionState.CONNECTED, now)
                    mothlight.realtime.record("demo", FrameDirection.INCOMING, """{"item":$id,"token":"demo-secret"}""", now)
                }
            }
        })
        layout.addView(Button(this).apply {
            setText(R.string.diagnostic_scenario)
            setOnClickListener { diagnosticScenario(mothlight) }
        })
        layout.addView(createMothlightView(this, mothlight, tools), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }
    private fun request() {
        pending?.cancel()
        pending = api.items().also { call ->
            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {
                    response.body()?.close()
                    response.errorBody()?.close()
                }
                override fun onFailure(call: Call<ResponseBody>, error: Throwable) = Unit
            })
        }
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
