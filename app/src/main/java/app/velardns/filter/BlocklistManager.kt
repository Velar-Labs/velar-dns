package app.velardns.filter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Gerencia o download e armazenamento das listas de domínios maliciosos.
 *
 * As listas são baixadas da internet, combinadas num arquivo local e
 * carregadas no [DomainMatcher] em memória.
 */
object BlocklistManager {

    private const val TAG = "BlocklistManager"

    /** Diretório interno onde as listas ficam salvas. */
    private const val DIR = "blocklists"

    /** Arquivo combinado com todos os domínios. */
    private const val MERGED_FILE = "merged.txt"

    /** Fontes públicas, gratuitas e mantidas pela comunidade. */
    val SOURCES = listOf(
        BlocklistSource(
            id = "stevenblack",
            name = "Steven Black Hosts",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            description = "Malware, phishing e rastreadores (~170 mil domínios)",
        ),
        BlocklistSource(
            id = "urlhaus",
            name = "URLhaus (abuse.ch)",
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            description = "Domínios de distribuição de malware",
        ),
        BlocklistSource(
            id = "phishing_army",
            name = "Phishing Army",
            url = "https://phishing.army/download/phishing_army_blocklist.txt",
            description = "Domínios de phishing confirmados",
        ),
    )

    val matcher = DomainMatcher()

    /** Lista branca do usuário. */
    @Volatile
    var whitelist: Set<String> = emptySet()
        private set

    private lateinit var appDir: File

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        appDir = File(context.filesDir, DIR).also { it.mkdirs() }
        loadWhitelist(context)
        // Carrega as listas em cache, se existirem.
        val merged = File(appDir, MERGED_FILE)
        if (merged.exists() && merged.length() > 0) {
            try {
                matcher.load(merged.bufferedReader().lineSequence())
                Log.i(TAG, "Listas carregadas do cache: ${matcher.size} domínios")
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao carregar cache", e)
            }
        }
    }

    /**
     * Verifica se um domínio deve ser bloqueado.
     * Respeita a whitelist do usuário.
     */
    fun shouldBlock(domain: String): Boolean {
        val normalized = domain.lowercase().removePrefix("www.").trimEnd('.')
        if (normalized in whitelist) return false
        return matcher.isBlocked(normalized)
    }

    /**
     * Baixa todas as listas, combina num arquivo e recarrega o matcher.
     * Retorna o número total de domínios carregados.
     */
    suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val downloaded = mutableListOf<File>()

        for (source in SOURCES) {
            try {
                val file = downloadList(source)
                downloaded.add(file)
                Log.i(TAG, "Baixada: ${source.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao baixar ${source.name}", e)
                errors.add(source.name)
                // Usa o cache anterior se existir
                val cached = File(appDir, "${source.id}.txt")
                if (cached.exists()) downloaded.add(cached)
            }
        }

        // Combinar tudo num arquivo
        val merged = File(appDir, MERGED_FILE)
        merged.outputStream().bufferedWriter().use { writer ->
            for (file in downloaded) {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { writer.appendLine(it) }
                }
            }
        }

        // Recarregar o matcher
        matcher.load(merged.bufferedReader().lineSequence())
        Log.i(TAG, "Listas atualizadas: ${matcher.size} domínios, ${errors.size} erros")

        UpdateResult(
            totalDomains = matcher.size,
            sourcesOk = SOURCES.size - errors.size,
            sourcesTotal = SOURCES.size,
            errors = errors,
        )
    }

    private fun downloadList(source: BlocklistSource): File {
        val request = Request.Builder().url(source.url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val file = File(appDir, "${source.id}.txt")
        response.body?.let { body ->
            file.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
        }
        return file
    }

    // ---- Whitelist do usuário ----

    private const val WHITELIST_PREFS = "velar_whitelist"

    private fun loadWhitelist(context: Context) {
        val sp = context.getSharedPreferences(WHITELIST_PREFS, Context.MODE_PRIVATE)
        whitelist = sp.getStringSet("domains", emptySet())?.toSet() ?: emptySet()
    }

    fun addToWhitelist(context: Context, domain: String) {
        val normalized = domain.lowercase().removePrefix("www.").trimEnd('.')
        whitelist = whitelist + normalized
        saveWhitelist(context)
    }

    fun removeFromWhitelist(context: Context, domain: String) {
        val normalized = domain.lowercase().removePrefix("www.").trimEnd('.')
        whitelist = whitelist - normalized
        saveWhitelist(context)
    }

    private fun saveWhitelist(context: Context) {
        context.getSharedPreferences(WHITELIST_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("domains", whitelist.toSet())
            .apply()
    }

    // ---- Timestamp da última atualização ----

    fun lastUpdateMillis(context: Context): Long {
        return context.getSharedPreferences("velar", Context.MODE_PRIVATE)
            .getLong("blocklist_updated_at", 0L)
    }

    fun saveLastUpdate(context: Context) {
        context.getSharedPreferences("velar", Context.MODE_PRIVATE)
            .edit()
            .putLong("blocklist_updated_at", System.currentTimeMillis())
            .apply()
    }
}

data class BlocklistSource(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
)

data class UpdateResult(
    val totalDomains: Int,
    val sourcesOk: Int,
    val sourcesTotal: Int,
    val errors: List<String>,
)
