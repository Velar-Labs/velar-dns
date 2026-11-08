package app.velardns.stats

import android.content.Context
import android.util.Log
import app.velardns.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Contador anônimo de aparelhos.
 *
 * Cada instalação gera um código aleatório (UUID) guardado só no aparelho.
 * Com a proteção ligada, o app avisa o servidor a cada 30 minutos.
 * Não envia nome, conta, IP salvo, sites visitados nem consultas DNS.
 */
object DeviceCounter {

    private const val TAG = "DeviceCounter"
    private const val URL = "https://znfgghkuzakrmjazundg.supabase.co/rest/v1/rpc/ping"
    // Chave pública: só permite chamar ping() e stats(). Não lê a tabela.
    private const val KEY = "sb_publishable_PpNazlFQGbp68MIeCwctmQ_c61Y3R7O"
    private const val INTERVAL_MS = 30 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun deviceId(context: Context): String {
        val sp = context.getSharedPreferences("velar_device", Context.MODE_PRIVATE)
        sp.getString("id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        sp.edit().putString("id", id).apply()
        return id
    }

    /** Envia um aviso. Falhas são ignoradas: o contador nunca atrapalha o DNS. */
    fun ping(context: Context) {
        try {
            val body = """{"device":"${deviceId(context)}","ver":"${BuildConfig.VERSION_NAME}"}"""
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(URL)
                .header("apikey", KEY)
                .post(body)
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) Log.w(TAG, "ping HTTP ${r.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ping falhou: ${e.message}")
        }
    }

    /** Avisa agora e a cada 30 min enquanto o job estiver vivo. */
    fun startLoop(context: Context, scope: CoroutineScope): Job = scope.launch {
        val app = context.applicationContext
        while (isActive) {
            ping(app)
            delay(INTERVAL_MS)
        }
    }
}
