package app.velardns.data

import android.content.Context
import app.velardns.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Avisa quando existe uma versão mais nova na página de Releases do GitHub.
 * Faz uma única consulta pública (sem identificar o aparelho), no máximo a cada 12 h,
 * e só se o usuário não desligou a opção em Ajustes. Nunca instala nada sozinho.
 */
object UpdateChecker {

    private const val URL = "https://api.github.com/repos/Velar-Labs/velar-dns/releases/latest"
    private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val _available = MutableStateFlow<String?>(null)
    /** Número da versão nova (ex.: "1.4.0"), ou null se o app está em dia / não foi verificado. */
    val available: StateFlow<String?> = _available.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    fun checkIfDue(context: Context) {
        if (!Prefs.current.checkUpdates) return
        val sp = context.applicationContext.getSharedPreferences("velar", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("update_checked_at", 0L) < INTERVAL_MS) {
            // Reaproveita o resultado da última checagem, sem usar a rede.
            _available.value = sp.getString("update_latest", null)?.takeIf { isNewer(it, BuildConfig.VERSION_NAME) }
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val latest = runCatching { fetchLatest() }.getOrNull() ?: return@launch
            sp.edit().putLong("update_checked_at", now).putString("update_latest", latest).apply()
            _available.value = latest.takeIf { isNewer(it, BuildConfig.VERSION_NAME) }
        }
    }

    private fun fetchLatest(): String? {
        val request = Request.Builder()
            .url(URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VelarDNS/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val tag = JSONObject(r.body?.string() ?: return null).optString("tag_name")
            return tag.removePrefix("v").ifBlank { null }
        }
    }

    /** true se [candidate] (ex.: "1.4.0") é maior que [current] (ex.: "1.3.2"). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return false }
        val b = current.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
